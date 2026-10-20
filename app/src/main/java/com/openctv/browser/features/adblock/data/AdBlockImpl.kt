package com.openctv.browser.features.adblock.data

import com.openctv.browser.core.common.DispatcherProvider
import com.openctv.browser.core.common.Resource
import com.openctv.browser.features.adblock.domain.AdBlockContract
import com.openctv.browser.features.adblock.domain.AdBlockEngine
import com.openctv.browser.features.adblock.domain.BlockedRequestCounter
import com.openctv.browser.features.adblock.domain.FilterListsState
import com.openctv.browser.features.adblock.domain.HostUtils
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Orchestre le bloqueur : cache -> moteur au démarrage, mise à jour réseau si la liste est périmée,
 * sites désactivés (stockage -> moteur). Tout se passe hors du thread principal ; [scope] vit autant
 * que l'application. Sans liste, le moteur ne bloque rien.
 */
class AdBlockImpl(
    private val engine: AdBlockEngine,
    private val fetcher: FilterListFetcher,
    private val cache: FilterListCache,
    private val sitesStore: DisabledSitesStore,
    private val counter: BlockedRequestCounter,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val maxAge: Duration = DefaultMaxAge,
) : AdBlockContract {

    private val _filterLists = MutableStateFlow(FilterListsState())
    override val filterLists: StateFlow<FilterListsState> = _filterLists.asStateFlow()

    private val isStarted = AtomicBoolean(false)
    private val refreshLock = Mutex()

    override fun start() {
        if (!isStarted.compareAndSet(false, true)) return
        scope.launch(dispatchers.io) {
            sitesStore.sites.catch { }.collect { engine.updateDisabledSites(it) }
        }
        scope.launch(dispatchers.io) {
            // Sous le verrou des mises à jour : un cache lu avant un téléchargement concurrent
            // n'écrase jamais, dans le moteur, la liste plus récente installée entre-temps.
            refreshLock.withLock { loadCache() }
            if (isCacheStale()) refreshNow()
        }
    }

    override suspend fun refreshNow(): Resource<Unit> = withContext(dispatchers.io) {
        val isAlreadyRunning = !refreshLock.tryLock()
        if (isAlreadyRunning) return@withContext Resource.Loading
        try {
            _filterLists.update { it.copy(isUpdating = true, error = null) }
            download()
        } finally {
            _filterLists.update { it.copy(isUpdating = false) }
            refreshLock.unlock()
        }
    }

    override fun isEnabledForSite(site: String): Flow<Boolean> {
        val key = HostUtils.siteKeyOf(site) ?: return flowOf(true)
        return sitesStore.sites
            .map { disabled -> !HostUtils.isCoveredBy(key, disabled) }
            .catch { emit(true) }
    }

    override suspend fun setEnabledForSite(site: String, isEnabled: Boolean): Resource<Unit> {
        val key = HostUtils.siteKeyOf(site) ?: return Resource.Error("Site invalide")
        return withContext(dispatchers.io) {
            try {
                sitesStore.update { disabled -> if (isEnabled) disabled - key else disabled + key }
                Resource.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Resource.Error(e.message)
            }
        }
    }

    override fun blockedCount(site: String): Flow<Int> = counter.observe(site)

    private fun loadCache() {
        try {
            val filters = cache.readLines { FilterListParser.parse(it) }
            if (filters != null) engine.updateFilters(filters)
            _filterLists.update { it.copy(lastUpdated = cache.lastUpdated()) }
        } catch (e: Exception) {
            // Cache illisible : comme s'il n'y en avait pas, la mise à jour réseau prend le relais.
        }
    }

    private fun isCacheStale(): Boolean {
        val lastUpdated = _filterLists.value.lastUpdated ?: return true
        return Duration.between(lastUpdated, clock.instant()) > maxAge
    }

    /** L'ancienne liste (cache + moteur) n'est remplacée que si la nouvelle est téléchargée et non vide. */
    private fun download(): Resource<Unit> {
        var staged: FilterListCache.StagedList? = null
        return try {
            staged = cache.stage(fetcher.fetch())
            val filters = staged.readLines { FilterListParser.parse(it) }
            check(!filters.isEmpty) { "Liste de filtres vide" }
            staged.commit()
            engine.updateFilters(filters)
            _filterLists.update { it.copy(lastUpdated = cache.lastUpdated()) }
            Resource.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            _filterLists.update { it.copy(error = message) }
            Resource.Error(message)
        } finally {
            staged?.discard()
        }
    }

    companion object {
        val DefaultMaxAge: Duration = Duration.ofDays(4)
    }
}

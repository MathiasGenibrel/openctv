package com.openctv.browser.features.history.data

import com.openctv.browser.core.common.DispatcherProvider
import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.history.PageVisitsContract
import com.openctv.browser.features.history.domain.HistoryContract
import com.openctv.browser.features.history.domain.HistoryEntry
import com.openctv.browser.features.history.domain.HistoryPolicy
import java.time.Clock
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Historique Room : toutes les lectures et écritures sortent du thread principal. */
class HistoryImpl(
    private val dao: HistoryDao,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock = Clock.systemUTC(),
) : HistoryContract, PageVisitsContract {

    override fun getEntries(): Flow<Resource<List<HistoryEntry>>> {
        val entries = dao.observeAll()
            .map<List<HistoryEntryEntity>, Resource<List<HistoryEntry>>> { Resource.Success(it.map(HistoryEntryEntity::toDomain)) }
            .catch { emit(Resource.Error(it.message)) }
            .flowOn(dispatchers.io)
        return flow {
            emit(Resource.Loading)
            // Les entrées trop anciennes partent à l'ouverture de l'écran, pas seulement à la prochaine visite.
            withContext(dispatchers.io) { runCatching { prune() } }
            emitAll(entries)
        }
    }

    override suspend fun recordVisit(url: String, title: String): Resource<Unit> {
        if (!HistoryPolicy.isRecordable(url)) return Resource.Success(Unit)
        // Non annulable : quitter le navigateur juste après le chargement ne doit pas perdre la visite.
        return write(NonCancellable) {
            val latest = dao.latest()
            val now = Instant.now(clock)
            if (latest != null && HistoryPolicy.mergesWithLatest(latest.url, url)) {
                dao.update(latest.copy(title = HistoryPolicy.titleOf(latest.title, title), visitedAt = now))
            } else {
                dao.insert(HistoryEntryEntity(url = url.trim(), title = title.trim(), visitedAt = now))
            }
            prune()
        }
    }

    override suspend fun remove(id: Long): Resource<Unit> = write { dao.delete(id) }

    override suspend fun clear(): Resource<Unit> = write { dao.deleteAll() }

    private suspend fun prune() {
        dao.deleteOlderThan(HistoryPolicy.cutoff(Instant.now(clock)))
        dao.keepLatest(HistoryPolicy.MAX_ENTRIES)
    }

    private suspend fun write(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend () -> Unit,
    ): Resource<Unit> =
        withContext(dispatchers.io + context) {
            try {
                block()
                Resource.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Resource.Error(e.message)
            }
        }
}

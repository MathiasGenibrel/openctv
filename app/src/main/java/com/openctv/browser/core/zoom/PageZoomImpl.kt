package com.openctv.browser.core.zoom

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.openctv.browser.core.common.DispatcherProvider
import com.openctv.browser.core.common.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** Stockage persistant des zooms par site, sous leur forme [SiteZoomCodec]. */
interface SiteZoomStore {
    val entries: Flow<Set<String>>
    suspend fun update(transform: (Set<String>) -> Set<String>)
}

class DataStoreSiteZoomStore(private val dataStore: DataStore<Preferences>) : SiteZoomStore {
    private val key = stringSetPreferencesKey("site_zooms")

    override val entries: Flow<Set<String>> = dataStore.data.map { it[key].orEmpty() }

    override suspend fun update(transform: (Set<String>) -> Set<String>) {
        dataStore.edit { it[key] = transform(it[key].orEmpty()) }
    }
}

/** [scope] porte la lecture partagée : il vit aussi longtemps que l'application. */
class PageZoomImpl(
    private val store: SiteZoomStore,
    private val dispatchers: DispatcherProvider,
    scope: CoroutineScope,
) : PageZoomContract {
    override val sites: StateFlow<Resource<List<SiteZoom>>> = store.entries
        .map<Set<String>, Resource<List<SiteZoom>>> { stored ->
            Resource.Success(stored.mapNotNull(SiteZoomCodec::decode).sortedBy { it.host })
        }
        .catch { emit(Resource.Error(it.message)) }
        .flowOn(dispatchers.io)
        .stateIn(scope, SharingStarted.Eagerly, Resource.Loading)

    override suspend fun set(host: String, zoom: PageZoom): Resource<Unit> = write { stored ->
        stored.filterNot { SiteZoomCodec.decode(it)?.host == host }.toSet() + SiteZoomCodec.encode(SiteZoom(host, zoom))
    }

    override suspend fun remove(host: String): Resource<Unit> = write { stored ->
        stored.filterNot { SiteZoomCodec.decode(it)?.host == host }.toSet()
    }

    private suspend fun write(transform: (Set<String>) -> Set<String>): Resource<Unit> =
        withContext(dispatchers.io) {
            try {
                store.update(transform)
                Resource.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Resource.Error(e.message)
            }
        }
}

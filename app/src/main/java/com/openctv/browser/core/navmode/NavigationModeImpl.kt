package com.openctv.browser.core.navmode

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

/** Stockage persistant des hôtes en mode éléments. */
interface ElementHostsStore {
    val hosts: Flow<Set<String>>
    suspend fun update(transform: (Set<String>) -> Set<String>)
}

class DataStoreElementHostsStore(private val dataStore: DataStore<Preferences>) : ElementHostsStore {
    private val key = stringSetPreferencesKey("element_hosts")

    override val hosts: Flow<Set<String>> = dataStore.data.map { it[key].orEmpty() }

    override suspend fun update(transform: (Set<String>) -> Set<String>) {
        dataStore.edit { it[key] = transform(it[key].orEmpty()) }
    }
}

/** [scope] porte la lecture partagée : il vit aussi longtemps que l'application. */
class NavigationModeImpl(
    private val store: ElementHostsStore,
    private val dispatchers: DispatcherProvider,
    scope: CoroutineScope,
) : NavigationModeContract {
    override val elementHosts: StateFlow<Resource<Set<String>>> = store.hosts
        .map<Set<String>, Resource<Set<String>>> { Resource.Success(it) }
        .catch { emit(Resource.Error(it.message)) }
        .flowOn(dispatchers.io)
        .stateIn(scope, SharingStarted.Eagerly, Resource.Loading)

    override suspend fun set(host: String, mode: NavigationMode): Resource<Unit> =
        withContext(dispatchers.io) {
            try {
                store.update { stored -> if (mode == NavigationMode.Elements) stored + host else stored - host }
                Resource.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Resource.Error(e.message)
            }
        }
}

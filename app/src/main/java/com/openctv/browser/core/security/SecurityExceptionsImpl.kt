package com.openctv.browser.core.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.openctv.browser.core.common.DispatcherProvider
import com.openctv.browser.core.common.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Stockage persistant des exceptions, sous leur forme [SiteExceptionCodec]. */
interface SiteExceptionsStore {
    val entries: Flow<Set<String>>
    suspend fun update(transform: (Set<String>) -> Set<String>)
}

class DataStoreSiteExceptionsStore(private val dataStore: DataStore<Preferences>) : SiteExceptionsStore {
    private val key = stringSetPreferencesKey("site_exceptions")

    override val entries: Flow<Set<String>> = dataStore.data.map { it[key].orEmpty() }

    override suspend fun update(transform: (Set<String>) -> Set<String>) {
        dataStore.edit { it[key] = transform(it[key].orEmpty()) }
    }
}

/** [scope] porte la lecture partagée : il vit aussi longtemps que l'application. */
class SecurityExceptionsImpl(
    private val store: SiteExceptionsStore,
    private val dispatchers: DispatcherProvider,
    scope: CoroutineScope,
) : SecurityExceptionsContract {
    private val session = MutableStateFlow<Set<SiteException>>(emptySet())

    override val persisted: StateFlow<Resource<List<SiteException>>> = store.entries
        .map<Set<String>, Resource<List<SiteException>>> { stored ->
            Resource.Success(stored.mapNotNull(SiteExceptionCodec::decode).sortedBy { it.host })
        }
        .catch { emit(Resource.Error(it.message)) }
        .flowOn(dispatchers.io)
        .stateIn(scope, SharingStarted.Eagerly, Resource.Loading)

    override fun isAllowed(exception: SiteException): Boolean {
        val isAllowedForSession = exception in session.value
        val isRemembered = (persisted.value as? Resource.Success)?.data?.contains(exception) == true
        return isAllowedForSession || isRemembered
    }

    override suspend fun allow(exception: SiteException, isRemembered: Boolean): Resource<Unit> {
        session.update { it + exception }
        if (!isRemembered) return Resource.Success(Unit)
        return write { it + SiteExceptionCodec.encode(exception) }
    }

    override suspend fun remove(exception: SiteException): Resource<Unit> {
        session.update { it - exception }
        return write { it - SiteExceptionCodec.encode(exception) }
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

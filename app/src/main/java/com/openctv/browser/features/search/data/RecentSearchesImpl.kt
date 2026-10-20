package com.openctv.browser.features.search.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.openctv.browser.core.common.Resource
import com.openctv.browser.features.search.domain.RecentSearchList
import com.openctv.browser.features.search.domain.RecentSearchesContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Liste stockée dans une seule préférence, une requête par ligne (les requêtes n'en contiennent jamais). */
class RecentSearchesImpl(private val dataStore: DataStore<Preferences>) : RecentSearchesContract {

    override fun getRecents(): Flow<Resource<List<String>>> = dataStore.data
        .map<Preferences, Resource<List<String>>> { Resource.Success(it.queries()) }
        .onStart { emit(Resource.Loading) }
        .catch { emit(Resource.Error(it.message)) }

    override suspend fun record(query: String): Resource<Unit> =
        write { preferences -> preferences.store(RecentSearchList.add(preferences.queries(), query)) }

    override suspend fun clear(): Resource<Unit> = write { preferences -> preferences.remove(Key) }

    private suspend fun write(change: (MutablePreferences) -> Unit): Resource<Unit> =
        try {
            dataStore.edit(change)
            Resource.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resource.Error(e.message)
        }

    private fun Preferences.queries(): List<String> =
        this[Key]?.split(Separator)?.filter { it.isNotEmpty() }.orEmpty()

    private fun MutablePreferences.store(queries: List<String>) {
        this[Key] = queries.joinToString(Separator)
    }

    private companion object {
        val Key = stringPreferencesKey("queries")
        const val Separator = "\n"
    }
}

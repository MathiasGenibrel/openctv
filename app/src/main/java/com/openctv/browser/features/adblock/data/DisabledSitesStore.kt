package com.openctv.browser.features.adblock.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Stockage persistant des sites où le blocage est désactivé : source de vérité du moteur. */
interface DisabledSitesStore {
    val sites: Flow<Set<String>>
    suspend fun update(transform: (Set<String>) -> Set<String>)
}

class DataStoreDisabledSitesStore(private val dataStore: DataStore<Preferences>) : DisabledSitesStore {
    private val key = stringSetPreferencesKey("disabled_sites")

    override val sites: Flow<Set<String>> = dataStore.data.map { it[key].orEmpty() }

    override suspend fun update(transform: (Set<String>) -> Set<String>) {
        dataStore.edit { it[key] = transform(it[key].orEmpty()) }
    }
}

package com.openctv.browser.features.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.openctv.browser.features.settings.domain.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreSettingsStore(private val dataStore: DataStore<Preferences>) : SettingsStore {
    override val settings: Flow<Settings> = dataStore.data.map(SettingsPreferencesCodec::decode)

    override suspend fun update(transform: (Settings) -> Settings) {
        dataStore.edit { preferences ->
            val updated = transform(SettingsPreferencesCodec.decode(preferences))
            SettingsPreferencesCodec.encode(updated, into = preferences)
        }
    }
}

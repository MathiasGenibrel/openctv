package com.openctv.browser.features.settings.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.openctv.browser.core.common.DispatcherProvider
import com.openctv.browser.features.settings.data.DataStoreSettingsStore
import com.openctv.browser.features.settings.data.SettingsImpl
import com.openctv.browser.features.settings.data.SettingsStore
import com.openctv.browser.features.settings.domain.SettingsContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

val settingsModule = module {
    single<SettingsStore> { DataStoreSettingsStore(androidContext().settingsDataStore) }
    single<SettingsContract> {
        val dispatchers = get<DispatcherProvider>()
        SettingsImpl(
            store = get(),
            dispatchers = dispatchers,
            scope = CoroutineScope(SupervisorJob() + dispatchers.default),
        )
    }
}

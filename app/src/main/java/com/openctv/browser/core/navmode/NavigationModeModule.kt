package com.openctv.browser.core.navmode

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.openctv.browser.core.common.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val Context.navigationModeDataStore by preferencesDataStore(name = "navigation_mode")

val navigationModeModule = module {
    single<ElementHostsStore> { DataStoreElementHostsStore(androidContext().navigationModeDataStore) }
    single<NavigationModeContract> {
        val dispatchers = get<DispatcherProvider>()
        NavigationModeImpl(
            store = get(),
            dispatchers = dispatchers,
            scope = CoroutineScope(SupervisorJob() + dispatchers.default),
        )
    }
}

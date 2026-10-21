package com.openctv.browser.core.zoom

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.openctv.browser.core.common.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val Context.pageZoomDataStore by preferencesDataStore(name = "page_zoom")

val zoomModule = module {
    single<SiteZoomStore> { DataStoreSiteZoomStore(androidContext().pageZoomDataStore) }
    single<PageZoomContract> {
        val dispatchers = get<DispatcherProvider>()
        PageZoomImpl(
            store = get(),
            dispatchers = dispatchers,
            scope = CoroutineScope(SupervisorJob() + dispatchers.default),
        )
    }
}

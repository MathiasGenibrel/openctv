package com.openctv.browser.features.adblock.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.openctv.browser.core.common.DispatcherProvider
import com.openctv.browser.core.webview.CosmeticSelectionSource
import com.openctv.browser.core.webview.RequestInterceptor
import com.openctv.browser.features.adblock.data.AdBlockImpl
import com.openctv.browser.features.adblock.data.AdBlockInterceptor
import com.openctv.browser.features.adblock.data.CookieBannerSelectionSource
import com.openctv.browser.features.adblock.data.DataStoreDisabledSitesStore
import com.openctv.browser.features.adblock.data.DisabledSitesStore
import com.openctv.browser.features.adblock.data.FileFilterListCache
import com.openctv.browser.features.adblock.data.FilterListCache
import com.openctv.browser.features.adblock.data.FilterListFetcher
import com.openctv.browser.features.adblock.data.OkHttpFilterListFetcher
import com.openctv.browser.features.adblock.domain.AdBlockContract
import com.openctv.browser.features.adblock.domain.AdBlockEngine
import com.openctv.browser.features.adblock.domain.BlockedRequestCounter
import com.openctv.browser.features.adblock.domain.DefaultAdBlockEngine
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val Context.adBlockDataStore by preferencesDataStore(name = "adblock")

val adBlockModule = module {
    single<AdBlockEngine> { DefaultAdBlockEngine() }
    single { BlockedRequestCounter() }
    single<FilterListFetcher> { OkHttpFilterListFetcher(client = lazy { get<OkHttpClient>() }) }
    single<FilterListCache> { FileFilterListCache(androidContext().filesDir, Clock.systemUTC()) }
    single<DisabledSitesStore> { DataStoreDisabledSitesStore(androidContext().adBlockDataStore) }
    single<AdBlockContract> {
        val dispatchers = get<DispatcherProvider>()
        AdBlockImpl(
            engine = get(),
            fetcher = get(),
            cache = get(),
            sitesStore = get(),
            counter = get(),
            dispatchers = dispatchers,
            scope = CoroutineScope(SupervisorJob() + dispatchers.default),
            clock = Clock.systemUTC(),
        )
    }
    single<CosmeticSelectionSource> { CookieBannerSelectionSource(engine = get(), settings = get()) }
    single<RequestInterceptor> { AdBlockInterceptor(engine = get(), settings = get(), counter = get()) }
}

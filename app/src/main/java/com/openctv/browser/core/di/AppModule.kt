package com.openctv.browser.core.di

import com.openctv.browser.core.common.DefaultDispatcherProvider
import com.openctv.browser.core.common.DispatcherProvider
import com.openctv.browser.core.database.databaseModule
import com.openctv.browser.core.network.networkModule
import com.openctv.browser.core.webview.webViewModule
import com.openctv.browser.features.adblock.di.adBlockModule
import com.openctv.browser.features.bookmarks.di.bookmarksModule
import com.openctv.browser.features.settings.di.settingsModule
import org.koin.dsl.module

/** Module racine : les modules des features viendront s'ajouter à `appModules`. */
val appModule = module {
    single<DispatcherProvider> { DefaultDispatcherProvider() }
}

val appModules = listOf(appModule, networkModule, webViewModule, adBlockModule, databaseModule, bookmarksModule, settingsModule)

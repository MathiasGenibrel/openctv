package com.openctv.browser.core.di

import com.openctv.browser.core.common.DefaultDispatcherProvider
import com.openctv.browser.core.common.DispatcherProvider
import com.openctv.browser.core.database.databaseModule
import com.openctv.browser.features.adblock.di.adBlockModule
import com.openctv.browser.features.bookmarks.di.bookmarksModule
import org.koin.dsl.module

/** Module racine : les modules des features viendront s'ajouter à `appModules`. */
val appModule = module {
    single<DispatcherProvider> { DefaultDispatcherProvider() }
}

val appModules = listOf(appModule, adBlockModule, databaseModule, bookmarksModule)

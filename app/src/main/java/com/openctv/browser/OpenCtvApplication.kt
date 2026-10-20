package com.openctv.browser

import android.app.Application
import com.openctv.browser.core.di.appModules
import com.openctv.browser.features.adblock.domain.AdBlockContract
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.android.ext.android.get

class OpenCtvApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@OpenCtvApplication)
            modules(appModules)
        }
        // Cache puis mise à jour des listes de filtres : hors thread principal, sans bloquer la navigation.
        get<AdBlockContract>().start()
    }
}

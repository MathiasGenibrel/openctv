package com.openctv.browser

import android.app.Application
import com.openctv.browser.core.di.appModules
import com.openctv.browser.core.security.SecurityExceptionsContract
import com.openctv.browser.core.zoom.PageZoomContract
import com.openctv.browser.features.adblock.domain.AdBlockContract
import com.openctv.browser.features.settings.domain.SettingsContract
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.android.get
import org.koin.core.context.startKoin

class OpenCtvApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@OpenCtvApplication)
            modules(appModules)
        }
        // Lectures lancées au plus tôt, sans rien bloquer : construire ces objets est léger (le client HTTP
        // n'est créé qu'au premier téléchargement), leurs lectures partent sur le thread d'I/O. Préférences et
        // exceptions de sécurité d'abord, en parallèle (la page de démarrage attend les deux), puis cache des
        // listes de filtres et mise à jour.
        get<SettingsContract>()
        get<SecurityExceptionsContract>()
        get<PageZoomContract>()
        get<AdBlockContract>().start()
    }
}

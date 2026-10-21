package com.openctv.browser

import android.app.Application
import com.openctv.browser.core.di.appModules
import com.openctv.browser.features.adblock.domain.AdBlockContract
import com.openctv.browser.features.settings.domain.SettingsContract
import kotlin.concurrent.thread
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.android.get
import org.koin.core.context.startKoin

class OpenCtvApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Le thread principal ne fait que déclarer les modules (les singletons sont créés à la demande) :
        // un démarrage à froid lent retarde le premier écran de plusieurs secondes sur la TV.
        startKoin {
            androidContext(this@OpenCtvApplication)
            modules(appModules)
        }
        // Tout ce qui peut attendre part en arrière-plan : construction du client HTTP et du moteur de filtres,
        // ouverture des préférences (la page de démarrage en dépend), lecture du cache des listes puis mise à
        // jour. Koin crée ses singletons de façon synchronisée : l'activité qui les demande en même temps attend
        // la fin de leur construction au lieu d'en créer un second.
        thread(name = "openctv-startup") {
            get<SettingsContract>()
            get<AdBlockContract>().start()
        }
    }
}

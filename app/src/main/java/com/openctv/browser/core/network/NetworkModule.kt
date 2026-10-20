package com.openctv.browser.core.network

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.koin.dsl.module

/**
 * Client HTTP unique de l'application. Sert uniquement à télécharger les listes de filtres : aucun
 * intercepteur de log ni de mesure, aucun cookie, aucun cache disque.
 */
val networkModule = module {
    single {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.MINUTES)
            .build()
    }
}

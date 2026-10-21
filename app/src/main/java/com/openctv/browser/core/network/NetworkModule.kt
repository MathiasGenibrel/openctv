package com.openctv.browser.core.network

import java.util.concurrent.TimeUnit
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import org.koin.dsl.module

/**
 * Client HTTP unique de l'application. Sert uniquement à télécharger les listes de filtres : aucun
 * intercepteur de log ni de mesure, aucun cookie, aucun cache disque.
 *
 * **HTTPS seulement** : le manifest autorise le trafic en clair pour que la WebView puisse afficher une page HTTP
 * après avertissement (`usesCleartextTraffic`), ce qui l'autoriserait aussi ici. Sans `ConnectionSpec.CLEARTEXT`,
 * OkHttp refuse toute URL `http://`, redirections comprises (`UnknownServiceException`), avant toute connexion.
 */
fun filterListHttpClient(builder: OkHttpClient.Builder = OkHttpClient.Builder()): OkHttpClient =
    builder
        .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        .build()

val networkModule = module {
    single { filterListHttpClient() }
}

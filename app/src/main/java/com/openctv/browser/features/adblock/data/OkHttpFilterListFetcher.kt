package com.openctv.browser.features.adblock.data

import java.io.IOException
import java.io.InputStream
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Seules requêtes réseau de l'application hors navigation : le téléchargement des listes de filtres ([FilterListId], HTTPS).
 * [client] n'est construit qu'au premier téléchargement (sur le thread d'I/O) : son initialisation (TLS,
 * certificats système) ne pèse jamais sur le démarrage de l'application.
 */
class OkHttpFilterListFetcher(
    private val client: Lazy<OkHttpClient>,
    private val urlOf: (FilterListId) -> String = FilterListId::url,
) : FilterListFetcher {

    override fun fetch(list: FilterListId): InputStream {
        val request = Request.Builder().url(urlOf(list)).header("User-Agent", UserAgent).build()
        val response = client.value.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code}")
        }
        return response.body.byteStream()
    }

    companion object {
        private const val UserAgent = "OpenCTV"
    }
}

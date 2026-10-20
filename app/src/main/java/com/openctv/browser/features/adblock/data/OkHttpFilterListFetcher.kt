package com.openctv.browser.features.adblock.data

import java.io.IOException
import java.io.InputStream
import okhttp3.OkHttpClient
import okhttp3.Request

/** Seule requête réseau de l'application hors navigation : le téléchargement de la liste de filtres. */
class OkHttpFilterListFetcher(
    private val client: OkHttpClient,
    private val url: String = EasyListUrl,
) : FilterListFetcher {

    override fun fetch(): InputStream {
        val request = Request.Builder().url(url).header("User-Agent", UserAgent).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code}")
        }
        return response.body.byteStream()
    }

    companion object {
        const val EasyListUrl = "https://easylist.to/easylist/easylist.txt"
        private const val UserAgent = "OpenCTV"
    }
}

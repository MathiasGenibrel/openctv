package com.openctv.browser.core.webview

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import com.openctv.browser.core.common.DispatcherProvider
import com.openctv.browser.core.common.Resource
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Efface les données du moteur WebView. Tout se passe sur le thread principal (les API WebView l'exigent).
 *
 * Cookies, [WebStorage] et la base d'authentification HTTP sont des singletons du processus : aucune WebView n'est
 * nécessaire. Le cache HTTP, lui, n'est atteignable que par une instance : le cache de Chromium est partagé par toutes
 * les WebView du processus, donc une instance temporaire, sans page ni réglage, le vide aussi pour la WebView vivante,
 * puis est détruite aussitôt. Elle ne charge rien et n'est jamais affichée.
 *
 * Les données de formulaire (`WebViewDatabase.clearFormData`) ne sont pas appelées : sans effet depuis l'API 26, le
 * moteur ne les stocke plus.
 */
class WebViewDataCleaner(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : WebDataContract {

    override suspend fun clearSiteData(): Resource<Unit> = onMain {
        val cookies = CookieManager.getInstance()
        suspendCancellableCoroutine { continuation ->
            cookies.removeAllCookies { continuation.resume(Unit) }
        }
        cookies.flush()
        WebStorage.getInstance().deleteAllData()
        @Suppress("DEPRECATION")
        WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
    }

    override suspend fun clearCache(): Resource<Unit> = onMain {
        val webView = WebView(context)
        try {
            webView.clearCache(true)
        } finally {
            webView.destroy()
        }
    }

    private suspend fun onMain(block: suspend () -> Unit): Resource<Unit> =
        withContext(dispatchers.main) {
            try {
                block()
                Resource.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Resource.Error(e.message)
            }
        }
}

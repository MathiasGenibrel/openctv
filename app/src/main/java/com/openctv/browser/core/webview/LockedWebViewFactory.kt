package com.openctv.browser.core.webview

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebSettings
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewOutcomeReceiver
import androidx.webkit.WebViewStartUpConfig
import androidx.webkit.WebViewStartUpResult
import androidx.webkit.WebViewStartupException
import java.util.concurrent.Executors

/**
 * Seul endroit qui crée et configure une `WebView`. Aucune feature ne touche `WebSettings`.
 * Réglages verrouillés : pas d'accès fichier/contenu, contenu mixte interdit, Safe Browsing si
 * disponible, aucune interface JavaScript exposée aux pages.
 */
class LockedWebViewFactory(
    private val requestInterceptor: RequestInterceptor = RequestInterceptor.PassThrough,
) {
    /**
     * Pré-chauffe le moteur (chargement du fournisseur WebView) hors du thread principal, pour que la création
     * de la première WebView ne bloque pas l'écran. Best-effort : un moteur trop ancien pour `startUpWebView`
     * est ignoré, la première WebView paiera simplement le démarrage. Ne crée ni ne configure aucune WebView.
     */
    fun warmUp(context: Context) {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val config = WebViewStartUpConfig.Builder(executor).build()
            WebViewCompat.startUpWebView(context.applicationContext, config, object : WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException> {
                override fun onResult(result: WebViewStartUpResult) = executor.shutdown()
                override fun onError(error: WebViewStartupException) = executor.shutdown()
            })
        } catch (_: Exception) {
            executor.shutdown()
        }
    }

    // JavaScript est indispensable au web moderne ; le reste est verrouillé ci-dessous.
    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context, listener: WebPageListener, fullscreenHost: FullscreenHost): PointerAwareWebView =
        PointerAwareWebView(context).apply {
            // Pas de petit carré bleu (contour de focus Android) sur la page : le curseur virtuel est le repère visuel.
            defaultFocusHighlightEnabled = false
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setGeolocationEnabled(false)
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                    WebSettingsCompat.setSafeBrowsingEnabled(this, true)
                }
            }
            webViewClient = SecureWebViewClient(listener, requestInterceptor)
            webChromeClient = SecureWebChromeClient(listener, fullscreenHost)
        }
}

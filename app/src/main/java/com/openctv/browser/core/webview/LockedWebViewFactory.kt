package com.openctv.browser.core.webview

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebSettings
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Seul endroit qui crée et configure une `WebView`. Aucune feature ne touche `WebSettings`.
 * Réglages verrouillés : pas d'accès fichier/contenu, contenu mixte interdit, Safe Browsing si
 * disponible, aucune interface JavaScript exposée aux pages.
 */
class LockedWebViewFactory(
    private val requestInterceptor: RequestInterceptor = RequestInterceptor.PassThrough,
) {
    // JavaScript est indispensable au web moderne ; le reste est verrouillé ci-dessous.
    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context, listener: WebPageListener, fullscreenHost: FullscreenHost): PointerAwareWebView =
        PointerAwareWebView(context).apply {
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

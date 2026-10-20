package com.openctv.browser.core.webview

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/** Client de base : navigation limitée à http/https, SSL toujours annulé, erreurs de page remontées. */
class SecureWebViewClient(
    private val listener: WebPageListener,
    private val requestInterceptor: RequestInterceptor = RequestInterceptor.PassThrough,
) : WebViewClient() {

    /** Écrite sur le thread principal, lue depuis le thread de `shouldInterceptRequest`. */
    @Volatile
    private var currentPageUrl: String? = null

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val isAllowed = WebNavigationPolicy.isUrlAllowed(request.url.toString())
        return !isAllowed
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
        requestInterceptor.intercept(request.url.toString(), currentPageUrl, request.isForMainFrame)

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        currentPageUrl = url
        listener.onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        listener.onPageFinished(url)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        listener.onUrlChanged(url, view.canGoBack(), view.canGoForward())
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) {
            listener.onLoadError(WebLoadError(request.url.toString(), loadErrorCauseOf(error.errorCode)))
        }
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        if (SslErrorPolicy.isMainFrameError(error.url, currentPageUrl)) {
            listener.onLoadError(WebLoadError(error.url, LoadErrorCause.InvalidCertificate))
        }
    }
}

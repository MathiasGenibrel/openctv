package com.openctv.browser.core.webview

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.openctv.browser.core.security.NoSecurityExceptions
import com.openctv.browser.core.security.SecurityExceptionsContract
import com.openctv.browser.core.security.SiteException
import com.openctv.browser.core.security.SiteHost

/**
 * Client de base : navigation limitée à http/https, erreurs de page remontées.
 * - Page principale en HTTP : avertissement au lieu de la page, sauf exception ([WebNavigationPolicy.navigationOf]).
 * - SSL : `cancel()` par défaut ; `proceed()` seulement pour la page principale, avec une exception de
 *   l'utilisateur pour cet hôte et cette empreinte de certificat ([SslErrorPolicy.shouldProceed]).
 */
class SecureWebViewClient(
    private val listener: WebPageListener,
    private val requestInterceptor: RequestInterceptor = RequestInterceptor.PassThrough,
    private val securityExceptions: SecurityExceptionsContract = NoSecurityExceptions,
) : WebViewClient() {

    /** Écrite sur le thread principal, lue depuis le thread de `shouldInterceptRequest`. */
    @Volatile
    private var currentPageUrl: String? = null

    private val mainFrame = MainFrameTracker()

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val requestUrl = request.url.toString()
        val decision = WebNavigationPolicy.navigationOf(requestUrl, request.isForMainFrame) { host ->
            securityExceptions.isAllowed(SiteException.Http(host))
        }
        return when (decision) {
            // Seule une navigation réellement suivie (lien, redirection) devient le cadre principal attendu.
            NavigationDecision.Allow -> false.also { mainFrame.onRequest(requestUrl, request.isForMainFrame) }
            NavigationDecision.Refuse -> true
            is NavigationDecision.WarnHttp -> true.also {
                val warning = SecurityWarning.InsecureHttp(requestUrl, decision.host)
                listener.onLoadError(WebLoadError(requestUrl, LoadErrorCause.Unknown, warning))
            }
        }
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val requestUrl = request.url.toString()
        // `loadUrl` ne passe pas par shouldOverrideUrlLoading : c'est ici qu'on voit la page principale demandée.
        mainFrame.onRequest(requestUrl, request.isForMainFrame)
        return requestInterceptor.intercept(requestUrl, currentPageUrl, request.isForMainFrame)
    }

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
        val isMainFrameError = mainFrame.isMainFrameError(error.url)
        // Sous-ressource : toujours annulée, sans même lire le certificat.
        if (!isMainFrameError) return handler.cancel()

        val certificate = CertificateDetails.of(error.certificate)
        val shouldProceed = SslErrorPolicy.shouldProceed(
            isMainFrameError = true,
            errorUrl = error.url,
            fingerprint = certificate.fingerprint,
            isAllowed = securityExceptions::isAllowed,
        )
        if (shouldProceed) {
            handler.proceed()
            listener.onUnverifiedCertificateAccepted(error.url)
            return
        }
        handler.cancel()
        val warning = SecurityWarning.InvalidCertificate(
            url = error.url,
            host = SiteHost.of(error.url),
            problem = certificateProblemOf(error.primaryError),
            issuedTo = certificate.issuedTo,
            validUntil = certificate.validUntil,
            fingerprint = certificate.fingerprint,
        )
        listener.onLoadError(WebLoadError(error.url, LoadErrorCause.InvalidCertificate, warning))
    }
}

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
import com.openctv.browser.core.security.SiteHost

/**
 * Client de base : navigation limitée à http/https, erreurs de page remontées. Adaptateur Android de
 * [PageSecurityGuard], qui décide :
 * - page principale en HTTP : avertissement au lieu de la page, sauf exception (`shouldOverrideUrlLoading`, et
 *   revérifiée à `onPageStarted` pour les chemins qui n'y passent pas : historique, rechargement, POST) ;
 * - SSL : `cancel()` par défaut ; `proceed()` seulement pour la page principale, avec une exception de
 *   l'utilisateur pour cet hôte et cette empreinte de certificat ([SslErrorPolicy.shouldProceed]) ;
 * - page affichée par un `proceed()` retenu par le moteur : badge maintenu, ou rechargement si l'exception a été retirée.
 */
class SecureWebViewClient(
    private val listener: WebPageListener,
    private val requestInterceptor: RequestInterceptor = RequestInterceptor.PassThrough,
    securityExceptions: SecurityExceptionsContract = NoSecurityExceptions,
) : WebViewClient() {

    /** Écrite sur le thread principal, lue depuis le thread de `shouldInterceptRequest`. */
    @Volatile
    private var currentPageUrl: String? = null

    private val guard = PageSecurityGuard(securityExceptions)

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val requestUrl = request.url.toString()
        return when (val decision = guard.navigationOf(requestUrl, request.isForMainFrame)) {
            // Seule une navigation réellement suivie (lien, redirection) devient le cadre principal attendu.
            NavigationDecision.Allow -> false.also { guard.onMainFrameRequest(requestUrl, request.isForMainFrame) }
            NavigationDecision.Refuse -> true
            is NavigationDecision.WarnHttp -> true.also { warnInsecureHttp(decision.warningFor(requestUrl)) }
        }
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val requestUrl = request.url.toString()
        // `loadUrl` ne passe pas par shouldOverrideUrlLoading : c'est ici qu'on voit la page principale demandée.
        guard.onMainFrameRequest(requestUrl, request.isForMainFrame)
        return requestInterceptor.intercept(requestUrl, currentPageUrl, request.isForMainFrame)
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        val check = guard.checkShownPage(url) { CertificateDetails.of(view.certificate).fingerprint }
        if (check is ShownPageCheck.HttpNotAllowed) {
            // Arrivée sans décision (historique, POST…) : la page ne va pas plus loin, l'avertissement la remplace.
            view.stopLoading()
            warnInsecureHttp(check.warning)
            return
        }
        currentPageUrl = url
        listener.onPageStarted(url)
        onCertificateCheck(view, url, check)
    }

    override fun onPageFinished(view: WebView, url: String) {
        listener.onPageFinished(url)
        // Le certificat de la page n'est pas forcément connu dès onPageStarted : seconde vérification.
        val check = guard.checkShownPage(url) { CertificateDetails.of(view.certificate).fingerprint }
        onCertificateCheck(view, url, check)
    }

    private fun onCertificateCheck(view: WebView, url: String, check: ShownPageCheck) {
        when (check) {
            ShownPageCheck.UnverifiedCertificate -> listener.onUnverifiedCertificateAccepted(url)
            ShownPageCheck.CertificateRevoked -> {
                // L'exception a été retirée : le moteur oublie son proceed() et la page repasse par onReceivedSslError.
                view.stopLoading()
                view.clearSslPreferences()
                view.reload()
            }
            ShownPageCheck.Verified, is ShownPageCheck.HttpNotAllowed -> Unit
        }
    }

    private fun warnInsecureHttp(warning: SecurityWarning.InsecureHttp) {
        listener.onLoadError(WebLoadError(warning.url, LoadErrorCause.Unknown, warning))
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
        // Sous-ressource : toujours annulée, sans même lire le certificat.
        if (!guard.isMainFrameError(error.url)) return handler.cancel()

        // Empreinte du certificat présenté par CETTE connexion (pas celui de la page précédente).
        val certificate = CertificateDetails.of(error.certificate)
        if (guard.onSslError(error.url, certificate.fingerprint)) {
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

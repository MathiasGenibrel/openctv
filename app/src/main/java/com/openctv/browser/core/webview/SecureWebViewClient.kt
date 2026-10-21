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
 *
 * Masquage des bandeaux de cookies (seconde exception à l'interdiction d'`evaluateJavascript`, cf. [CosmeticStyleScript]) :
 * le script de la page est préparé hors du thread principal dès la requête de la page principale, puis évalué une seule
 * fois, à son premier rendu (`onPageCommitVisible`, sinon `onPageFinished`), pour une page http/https que le garde
 * a laissé s'afficher. Jamais pour un avertissement, une page refusée ni une sous-ressource.
 */
class SecureWebViewClient(
    private val listener: WebPageListener,
    private val requestInterceptor: RequestInterceptor = RequestInterceptor.PassThrough,
    securityExceptions: SecurityExceptionsContract = NoSecurityExceptions,
    private val cosmetics: CosmeticSelectionSource = CosmeticSelectionSource.None,
) : WebViewClient() {

    private class PreparedStyle(val host: String, val script: String?)

    private val styleScript = CosmeticStyleScript()

    /** Écrite par le thread de `shouldInterceptRequest`, lue sur le thread principal. */
    @Volatile
    private var preparedStyle: PreparedStyle? = null

    /** Page courante autorisée à recevoir le style, et style déjà appliqué à cette page. Thread principal. */
    private var isStyleAllowed = false
    private var isStyleApplied = false

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
        if (request.isForMainFrame) prepareStyle(requestUrl)
        return requestInterceptor.intercept(requestUrl, currentPageUrl, request.isForMainFrame)
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        isStyleAllowed = false
        val check = guard.checkShownPage(url) { CertificateDetails.of(view.certificate).fingerprint }
        if (check is ShownPageCheck.HttpNotAllowed) {
            // Arrivée sans décision (historique, POST…) : la page ne va pas plus loin, l'avertissement la remplace.
            view.stopLoading()
            warnInsecureHttp(check.warning)
            return
        }
        currentPageUrl = url
        isStyleAllowed = WebNavigationPolicy.isUrlAllowed(url)
        isStyleApplied = false
        listener.onPageStarted(url)
        onCertificateCheck(view, url, check)
    }

    override fun onPageCommitVisible(view: WebView, url: String) {
        listener.onPageCommitVisible(url)
        applyStyle(view, url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        listener.onPageFinished(url)
        applyStyle(view, url)
        // Le certificat de la page n'est pas forcément connu dès onPageStarted : seconde vérification.
        val check = guard.checkShownPage(url) { CertificateDetails.of(view.certificate).fingerprint }
        onCertificateCheck(view, url, check)
    }

    /** Hors du thread principal : l'hôte demandé est connu avant le document, le script est prêt avant son rendu. */
    private fun prepareStyle(pageUrl: String) {
        val host = SiteHost.of(pageUrl) ?: return
        preparedStyle = PreparedStyle(host, scriptFor(pageUrl))
    }

    private fun scriptFor(pageUrl: String): String? = styleScript.build(cosmetics.selectionFor(pageUrl))

    private fun applyStyle(view: WebView, url: String) {
        if (!isStyleAllowed || isStyleApplied) return
        val host = SiteHost.of(url) ?: return
        isStyleApplied = true
        val prepared = preparedStyle
        // Redirection : la page affichée n'est pas celle demandée, le script se prépare alors pour son hôte.
        val script = if (prepared != null && prepared.host == host) prepared.script else scriptFor(url)
        if (script != null) view.evaluateJavascript(script, null)
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
            problem = certificateProblemOf(error.primaryError, certificate.validFrom, certificate.validUntil),
            issuedTo = certificate.issuedTo,
            validUntil = certificate.validUntil,
            fingerprint = certificate.fingerprint,
        )
        listener.onLoadError(WebLoadError(error.url, LoadErrorCause.InvalidCertificate, warning))
    }
}

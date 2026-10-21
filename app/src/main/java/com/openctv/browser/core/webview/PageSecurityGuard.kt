package com.openctv.browser.core.webview

import com.openctv.browser.core.security.SecurityExceptionsContract
import com.openctv.browser.core.security.SiteException
import com.openctv.browser.core.security.SiteHost

/**
 * Décisions de sécurité d'une WebView sur sa page principale, en Kotlin pur : [SecureWebViewClient] n'en est que
 * l'adaptateur Android. Une instance par WebView.
 *
 * Le moteur retient lui-même un `proceed()` (hôte + certificat) et ne rappelle plus `onReceivedSslError` pour ce
 * couple : sans suivi, une page revisitée perdrait son badge « Certificat non vérifié », et une exception retirée
 * resterait active. Le garde retient donc les certificats acceptés ([onSslError]) et revérifie chaque page
 * principale affichée ([checkShownPage]). La fabrique vide les décisions du moteur à chaque création de WebView
 * ([LockedWebViewFactory]) : tout certificat accepté sans erreur SSL l'a été par ce garde.
 *
 * Fils : [onMainFrameRequest] est appelé depuis `shouldInterceptRequest` (thread de fond, cf. [MainFrameTracker]) ;
 * tout le reste sur le thread principal.
 */
class PageSecurityGuard(private val exceptions: SecurityExceptionsContract) {
    private val mainFrame = MainFrameTracker()

    /** Empreintes acceptées par `proceed()` dans la vie de la WebView, par hôte. Thread principal seulement. */
    private val proceeded = mutableMapOf<String, MutableSet<String>>()

    /** Une requête vient d'être émise (ou une navigation suivie) : retient l'URL du cadre principal. */
    fun onMainFrameRequest(url: String, isMainFrame: Boolean) = mainFrame.onRequest(url, isMainFrame)

    /** Sort d'une navigation (cf. [WebNavigationPolicy.navigationOf]) selon les exceptions HTTP accordées. */
    fun navigationOf(url: String?, isMainFrame: Boolean): NavigationDecision =
        WebNavigationPolicy.navigationOf(url, isMainFrame, exceptions)

    /** L'erreur SSL sur [errorUrl] concerne-t-elle la page principale ? Sinon : `cancel()` sans lire le certificat. */
    fun isMainFrameError(errorUrl: String?): Boolean = mainFrame.isMainFrameError(errorUrl)

    /**
     * Erreur SSL de la page principale : `true` = `proceed()` (cf. [SslErrorPolicy.shouldProceed]), et le certificat
     * est retenu pour les pages suivantes de l'hôte, que le moteur ne signalera plus.
     */
    fun onSslError(errorUrl: String?, fingerprint: String?): Boolean {
        val shouldProceed = SslErrorPolicy.shouldProceed(
            isMainFrameError = isMainFrameError(errorUrl),
            errorUrl = errorUrl,
            fingerprint = fingerprint,
            isAllowed = exceptions::isAllowed,
        )
        val host = SiteHost.of(errorUrl)
        if (shouldProceed && host != null && fingerprint != null) proceeded.getOrPut(host) { mutableSetOf() } += fingerprint
        return shouldProceed
    }

    /**
     * Revérifie la page principale [url] qui s'affiche (`onPageStarted`, `onPageFinished`), y compris quand elle
     * n'est passée par aucune décision : historique (`goBack`), rechargement, formulaire POST, navigation sans
     * `shouldOverrideUrlLoading`. [fingerprint] (certificat affiché par la WebView) n'est calculé que pour un hôte
     * dont un certificat a été accepté.
     */
    fun checkShownPage(url: String, fingerprint: () -> String?): ShownPageCheck {
        val decision = navigationOf(url, isMainFrame = true)
        if (decision is NavigationDecision.WarnHttp) return ShownPageCheck.HttpNotAllowed(decision.warningFor(url))
        val isHttps = url.trim().startsWith("https://", ignoreCase = true)
        val host = SiteHost.of(url) ?: return ShownPageCheck.Verified
        val accepted = proceeded[host]
        val hasAcceptedCertificate = isHttps && !accepted.isNullOrEmpty()
        if (!hasAcceptedCertificate) return ShownPageCheck.Verified
        // Certificat illisible pour un hôte accepté : on ne peut pas prouver qu'il est valide, le badge reste.
        val shown = fingerprint() ?: return ShownPageCheck.UnverifiedCertificate
        val isAcceptedCertificate = accepted?.contains(shown) == true
        // Un autre certificat affiché sans erreur SSL a été validé par le moteur : page vérifiée.
        if (!isAcceptedCertificate) return ShownPageCheck.Verified
        if (exceptions.isAllowed(SiteException.Certificate(host, shown))) return ShownPageCheck.UnverifiedCertificate
        accepted?.remove(shown)
        return ShownPageCheck.CertificateRevoked
    }
}

/** Verdict de [PageSecurityGuard.checkShownPage] sur la page principale affichée. */
sealed interface ShownPageCheck {
    /** Rien à signaler (certificat valide, page HTTP autorisée, autre schéma). */
    data object Verified : ShownPageCheck

    /** Page affichée malgré un certificat invalide, par exception de l'utilisateur : la barre doit le dire. */
    data object UnverifiedCertificate : ShownPageCheck

    /**
     * Le certificat affiché avait été accepté, mais l'exception a été retirée depuis : oublier la décision du moteur
     * et recharger, pour que l'avertissement revienne.
     */
    data object CertificateRevoked : ShownPageCheck

    /** Page HTTP sans exception arrivée par un chemin non intercepté : l'arrêter, afficher l'avertissement. */
    data class HttpNotAllowed(val warning: SecurityWarning.InsecureHttp) : ShownPageCheck
}

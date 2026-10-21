package com.openctv.browser.core.webview

import com.openctv.browser.core.security.SiteException
import com.openctv.browser.core.security.SiteHost

/** Règles de sécurité de navigation, pures (sans Android) pour être testées en JUnit. */
object WebNavigationPolicy {
    private val AllowedPrefixes = listOf("http://", "https://")

    /** Seuls `http` et `https` sont chargés : `intent:`, `market:`, `file:`, `javascript:`… sont refusés. */
    fun isUrlAllowed(url: String?): Boolean {
        val candidate = url?.trim().orEmpty()
        return AllowedPrefixes.any { candidate.startsWith(it, ignoreCase = true) }
    }

    /**
     * Sort d'une navigation. Une page principale en HTTP n'est affichée que si son hôte fait l'objet d'une exception
     * ([isHttpAllowed]) ; sinon un avertissement la remplace. Les sous-ressources et cadres HTTP ne sont pas
     * concernés (le contenu mixte reste bloqué sur les pages HTTPS par la fabrique de la WebView).
     */
    fun navigationOf(url: String?, isMainFrame: Boolean, isHttpAllowed: (String) -> Boolean): NavigationDecision {
        if (!isUrlAllowed(url)) return NavigationDecision.Refuse
        val isInsecureMainFrame = isMainFrame && url.orEmpty().trim().startsWith("http://", ignoreCase = true)
        if (!isInsecureMainFrame) return NavigationDecision.Allow
        val host = SiteHost.of(url)
        val isAllowed = host != null && isHttpAllowed(host)
        return if (isAllowed) NavigationDecision.Allow else NavigationDecision.WarnHttp(host)
    }
}

/** Décision pour une navigation (cf. [WebNavigationPolicy.navigationOf]). */
sealed interface NavigationDecision {
    data object Allow : NavigationDecision
    data object Refuse : NavigationDecision

    /** Page principale en HTTP, sans exception : l'avertissement s'affiche à la place. [host] : voir [SiteHost]. */
    data class WarnHttp(val host: String?) : NavigationDecision
}

/**
 * Erreurs SSL : annulées par défaut. Seule la page principale peut être affichée malgré un certificat invalide,
 * et uniquement si l'utilisateur a accordé une exception pour cet hôte **et** cette empreinte de certificat.
 */
object SslErrorPolicy {
    /**
     * `proceed()` uniquement si l'erreur concerne la page principale ([isMainFrameError]) et que
     * (hôte de [errorUrl], [fingerprint]) fait l'objet d'une exception ([isAllowed]). Sous-ressources, empreinte
     * illisible, hôte illisible : toujours annulé.
     */
    fun shouldProceed(
        isMainFrameError: Boolean,
        errorUrl: String?,
        fingerprint: String?,
        isAllowed: (SiteException) -> Boolean,
    ): Boolean {
        if (!isMainFrameError) return false
        val host = SiteHost.of(errorUrl) ?: return false
        val knownFingerprint = fingerprint ?: return false
        return isAllowed(SiteException.Certificate(host, knownFingerprint))
    }

    /**
     * Une erreur SSL ne casse la page que si elle concerne le document principal, pas une sous-ressource.
     * [mainFrameUrl] est la dernière URL demandée pour le cadre principal (cf. [MainFrameTracker]) : la
     * comparaison tolère `/` final, fragment et casse du schéma/hôte (`loadUrl("https://a.org")` demande
     * `https://a.org/`).
     */
    fun isMainFrameError(errorUrl: String?, mainFrameUrl: String?): Boolean =
        errorUrl != null && mainFrameUrl != null && comparable(errorUrl) == comparable(mainFrameUrl)

    private fun comparable(url: String): String {
        val withoutFragment = url.trim().substringBefore('#')
        val schemeEnd = withoutFragment.indexOf("://")
        if (schemeEnd < 0) return withoutFragment
        val pathStart = withoutFragment.indexOf('/', schemeEnd + 3).takeIf { it >= 0 } ?: withoutFragment.length
        val origin = withoutFragment.substring(0, pathStart).lowercase()
        return origin + withoutFragment.substring(pathStart).trimEnd('/')
    }
}

/**
 * Mémorise l'URL du cadre principal dès qu'elle est demandée (`loadUrl`, lien suivi, redirection), avant
 * `onPageStarted` qui n'a pas forcément eu lieu quand l'erreur SSL arrive. Écrite depuis le thread de
 * `shouldInterceptRequest` et le thread principal, lue sur le thread principal : champ `@Volatile`.
 */
class MainFrameTracker {
    @Volatile
    var requestedUrl: String? = null
        private set

    fun onRequest(url: String, isMainFrame: Boolean) {
        if (isMainFrame) requestedUrl = url
    }

    fun isMainFrameError(errorUrl: String?): Boolean = SslErrorPolicy.isMainFrameError(errorUrl, requestedUrl)
}

/** Permissions demandées par les pages (caméra, micro, localisation…) : toujours refusées. */
object PagePermissionPolicy {
    @Suppress("UNUSED_PARAMETER")
    fun shouldGrant(resource: String): Boolean = false
}

/** Cause d'un échec de chargement de la page principale. */
enum class LoadErrorCause { Unreachable, InvalidCertificate, Unknown }

/** [warning] : avertissement de sécurité que l'utilisateur peut choisir de contourner (HTTP, certificat invalide). */
data class WebLoadError(val url: String?, val cause: LoadErrorCause, val warning: SecurityWarning? = null)

/** Traduit les codes `WebViewClient.ERROR_*` (valeurs constantes de l'API Android). */
internal fun loadErrorCauseOf(errorCode: Int): LoadErrorCause = when (errorCode) {
    ERROR_HOST_LOOKUP, ERROR_CONNECT, ERROR_IO, ERROR_TIMEOUT -> LoadErrorCause.Unreachable
    ERROR_FAILED_SSL_HANDSHAKE -> LoadErrorCause.InvalidCertificate
    else -> LoadErrorCause.Unknown
}

private const val ERROR_HOST_LOOKUP = -2
private const val ERROR_CONNECT = -6
private const val ERROR_IO = -7
private const val ERROR_TIMEOUT = -8
private const val ERROR_FAILED_SSL_HANDSHAKE = -11

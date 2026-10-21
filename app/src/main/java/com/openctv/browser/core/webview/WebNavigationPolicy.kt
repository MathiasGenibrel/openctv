package com.openctv.browser.core.webview

/** Règles de sécurité de navigation, pures (sans Android) pour être testées en JUnit. */
object WebNavigationPolicy {
    private val AllowedPrefixes = listOf("http://", "https://")

    /** Seuls `http` et `https` sont chargés : `intent:`, `market:`, `file:`, `javascript:`… sont refusés. */
    fun isUrlAllowed(url: String?): Boolean {
        val candidate = url?.trim().orEmpty()
        return AllowedPrefixes.any { candidate.startsWith(it, ignoreCase = true) }
    }
}

/** Erreurs SSL : la page n'est jamais chargée malgré un certificat invalide. */
object SslErrorPolicy {
    @Suppress("UNUSED_PARAMETER")
    fun shouldProceed(primaryError: Int): Boolean = false

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

data class WebLoadError(val url: String?, val cause: LoadErrorCause)

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

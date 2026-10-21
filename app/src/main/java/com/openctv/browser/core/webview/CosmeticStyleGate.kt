package com.openctv.browser.core.webview

import com.openctv.browser.core.security.SiteHost

/**
 * Quand, et avec quel script, masquer les bandeaux de cookies de la page principale. Décision en Kotlin pur :
 * [SecureWebViewClient] n'en est que l'adaptateur Android (comme pour [PageSecurityGuard]).
 *
 * - le script est préparé dès la requête de la page principale, hors du thread principal ([onMainFrameRequest]) :
 *   il est prêt avant le premier rendu ;
 * - il n'est rendu qu'**une fois par page** ([scriptToApply]), pour une page http/https que le garde a laissé
 *   s'afficher ([onPageStarted]) ;
 * - jamais pour une page remplacée par un avertissement, en échec ou rechargée par le garde ([onMainFrameBlocked]),
 *   que l'échec soit signalé avant ou après `onPageStarted` (page d'erreur du moteur), ni pour une sous-ressource ;
 * - redirection vers un autre hôte : le script est préparé pour l'hôte affiché.
 */
class CosmeticStyleGate(
    private val source: CosmeticSelectionSource,
    private val styleScript: CosmeticStyleScript = CosmeticStyleScript(),
) {
    private class PreparedStyle(val host: String, val script: String?)

    /** Écrite par le thread de `shouldInterceptRequest`, lue sur le thread principal. */
    @Volatile
    private var prepared: PreparedStyle? = null

    /** Dernière page principale en échec ou sous avertissement ; oubliée à la requête suivante. */
    @Volatile
    private var blockedUrl: String? = null

    /** Page courante autorisée à recevoir le style, et style déjà rendu pour elle. Thread principal. */
    private var isAllowed = false
    private var isApplied = false

    /** Requête de la page principale (thread de `shouldInterceptRequest`). Lectures en mémoire seulement. */
    fun onMainFrameRequest(pageUrl: String) {
        blockedUrl = null
        val host = SiteHost.of(pageUrl) ?: return
        prepared = PreparedStyle(host, scriptFor(pageUrl))
    }

    /** Page principale [url] que le garde laisse s'afficher. Thread principal. */
    fun onPageStarted(url: String) {
        val isBlocked = url == blockedUrl
        isAllowed = WebNavigationPolicy.isUrlAllowed(url) && !isBlocked
        isApplied = false
    }

    /** Page principale [url] remplacée par un avertissement, en échec ou rechargée : rien jusqu'à la requête suivante. */
    fun onMainFrameBlocked(url: String) {
        blockedUrl = url
        isAllowed = false
    }

    /** Script à évaluer au rendu de [url] ; `null` s'il n'y a rien à masquer, si la page n'y a pas droit ou l'a déjà eu. */
    fun scriptToApply(url: String): String? {
        if (!isAllowed || isApplied) return null
        val host = SiteHost.of(url) ?: return null
        isApplied = true
        // Le script préparé peut valoir `null` (rien à masquer) : on ne le recalcule que pour un autre hôte.
        val preparedForHost = prepared?.takeIf { it.host == host }
        return if (preparedForHost != null) preparedForHost.script else scriptFor(url)
    }

    private fun scriptFor(pageUrl: String): String? = styleScript.build(source.selectionFor(pageUrl))
}

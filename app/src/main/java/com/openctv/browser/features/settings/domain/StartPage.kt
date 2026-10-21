package com.openctv.browser.features.settings.domain

import com.openctv.browser.core.security.isHttpByDefaultHost
import com.openctv.browser.core.webview.WebNavigationPolicy
import java.net.URI

/** Page affichée au lancement d'OpenCTV. */
sealed interface StartPage {
    /** Écran d'accueil OpenCTV (par défaut). */
    data object Home : StartPage

    /** Dernière page visitée ; l'accueil tant qu'aucune page n'a été visitée. */
    data object LastPage : StartPage

    /** Un site précis, toujours une URL `http`/`https` valide (voir [fromInput]). */
    data class Site(val url: String) : StartPage

    companion object {
        val Default: StartPage = Home

        /**
         * Construit un [Site] depuis une saisie libre (« arte.tv », « http://exemple.org ») :
         * `https://` est ajouté sans schéma (`http://` pour un hôte de réseau privé). `null` si la saisie n'est pas une adresse web utilisable.
         */
        fun fromInput(raw: String): Site? {
            val trimmed = raw.trim()
            val hasSpace = trimmed.any { it.isWhitespace() }
            if (trimmed.isEmpty() || hasSpace) return null
            val hasScheme = trimmed.contains("://")
            val candidate = if (hasScheme) trimmed else "${defaultSchemeFor(trimmed)}://$trimmed"
            val isWebUrl = WebNavigationPolicy.isUrlAllowed(candidate)
            val host = runCatching { URI(candidate).host }.getOrNull()
            val hasHost = !host.isNullOrBlank()
            return if (isWebUrl && hasHost) Site(candidate) else null
        }

        /** `http` pour un hôte de réseau local (comme la barre d'adresse), `https` sinon. */
        private fun defaultSchemeFor(input: String): String {
            val host = runCatching { URI("http://$input").host }.getOrNull()?.lowercase()
            val isLocal = host != null && isHttpByDefaultHost(host)
            return if (isLocal) "http" else "https"
        }
    }
}

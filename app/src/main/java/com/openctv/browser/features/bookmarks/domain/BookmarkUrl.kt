package com.openctv.browser.features.bookmarks.domain

import java.net.URI

/**
 * Normalisation des URLs de favoris : deux écritures du même site donnent la même clé.
 * - espaces autour retirés, schéma et hôte en minuscules ;
 * - fragment (`#...`) retiré ;
 * - `/` final retiré (`https://arte.tv/` == `https://arte.tv`) ;
 * - chemin, requête et port conservés tels quels.
 * Une chaîne qui n'est pas une URL valide est seulement nettoyée (trim).
 */
object BookmarkUrl {
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return trimmed
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (scheme == null || host == null) return trimmed

        val port = if (uri.port == -1) "" else ":${uri.port}"
        val path = (uri.rawPath ?: "").trimEnd('/')
        val query = uri.rawQuery?.let { "?$it" } ?: ""
        return "$scheme://$host$port$path$query"
    }

    /**
     * Nom lisible du site : hôte sans `www.` (`https://www.arte.tv/fr` -> `arte.tv`), ou l'URL entière
     * si elle n'a pas d'hôte. Sert de titre par défaut à un favori.
     */
    fun siteName(url: String): String {
        val host = runCatching { URI(url).host }.getOrNull() ?: return url
        return host.removePrefix("www.")
    }
}

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
     * Clé de comparaison d'un site : [normalize] sans le schéma ni le `www.` initial. Une redirection
     * `wikipedia.org` -> `https://www.wikipedia.org` ou `http` -> `https` désigne le même favori.
     * Le favori stocké garde son adresse d'origine : seule la comparaison est tolérante.
     */
    fun key(raw: String): String {
        val normalized = normalize(raw)
        val withoutScheme = normalized.substringAfter("://", missingDelimiterValue = normalized)
        return withoutScheme.removePrefix("www.")
    }

    /**
     * Nom lisible du site : hôte sans `www.` ni port ni identifiants (`https://www.arte.tv/fr` -> `arte.tv`),
     * ou la saisie brute si aucun hôte n'est lisible. Titre par défaut d'un favori et libellé du navigateur.
     */
    fun siteName(url: String): String {
        val withoutScheme = url.trim().substringAfter("://")
        val authority = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').substringBefore(':').removePrefix("www.")
        return host.ifEmpty { url }
    }
}

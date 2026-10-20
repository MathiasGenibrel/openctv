package com.openctv.browser.features.home.domain

import com.openctv.browser.features.bookmarks.domain.Bookmark
import java.net.URI

/** Favori tel qu'affiché sur l'accueil : monogramme, nom et domaine. [index] fixe forme et couleur du monogramme. */
data class HomeFavorite(
    val url: String,
    val name: String,
    val domain: String,
    val monogram: String,
    val index: Int,
)

fun Bookmark.toHomeFavorite(index: Int): HomeFavorite {
    val domain = domainOf(url)
    val name = title.ifBlank { domain }
    val monogram = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    return HomeFavorite(url = url, name = name, domain = domain, monogram = monogram, index = index)
}

/** Hôte sans `www.` ; l'URL entière si elle n'a pas d'hôte. */
internal fun domainOf(url: String): String {
    val host = runCatching { URI(url).host }.getOrNull() ?: return url
    return host.removePrefix("www.")
}

package com.openctv.browser.features.search.domain

/** Règles de la liste des recherches récentes : plus récente d'abord, sans doublon, bornée. */
object RecentSearchList {
    const val MaxSize = 20
    private val Whitespace = Regex("\\s+")

    /** Requête nettoyée (espaces réduits), ou `null` si vide. */
    fun normalize(query: String): String? = query.trim().replace(Whitespace, " ").takeIf { it.isNotEmpty() }

    /** [query] en tête ; une requête déjà présente (casse ignorée) remonte au lieu d'être dupliquée. */
    fun add(current: List<String>, query: String): List<String> {
        val normalized = normalize(query) ?: return current
        val others = current.filterNot { it.equals(normalized, ignoreCase = true) }
        return (listOf(normalized) + others).take(MaxSize)
    }
}

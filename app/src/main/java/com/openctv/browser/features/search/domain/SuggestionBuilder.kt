package com.openctv.browser.features.search.domain

import com.openctv.browser.features.bookmarks.domain.Bookmark
import java.net.URI

/**
 * Suggestions locales : favoris correspondants puis recherches récentes. Aucun appel réseau.
 * Saisie vide : les recherches récentes seules (raccourci de relance).
 */
object SuggestionBuilder {
    const val MaxFavorites = 3
    const val MaxRecents = 5

    fun build(query: String, bookmarks: List<Bookmark>, recents: List<String>): List<Suggestion> {
        val needle = query.trim().lowercase()
        val isQueryEmpty = needle.isEmpty()
        if (isQueryEmpty) return recents.take(MaxRecents).map(Suggestion::Recent)

        val favorites = bookmarks
            .filter { it.matches(needle) }
            .take(MaxFavorites)
            .map { Suggestion.Favorite(url = it.url, title = it.title.ifBlank { hostOf(it.url) }) }
        val matchingRecents = recents
            .filter { it.lowercase().contains(needle) && !it.equals(needle, ignoreCase = true) }
            .take(MaxRecents)
            .map(Suggestion::Recent)
        return favorites + matchingRecents
    }

    private fun Bookmark.matches(needle: String): Boolean {
        val titleMatches = title.lowercase().contains(needle)
        val addressMatches = url.substringAfter("://").lowercase().contains(needle)
        return titleMatches || addressMatches
    }

    private fun hostOf(url: String): String =
        runCatching { URI(url).host }.getOrNull()?.removePrefix("www.") ?: url
}

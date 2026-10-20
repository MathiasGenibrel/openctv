package com.openctv.browser.features.settings.domain

import java.net.URLEncoder

/**
 * Moteurs de recherche proposés. [id] est la clé de persistance : ne jamais la renommer.
 * [isTrackingFree] pilote la mention « Sans pistage ».
 */
enum class SearchEngine(
    val id: String,
    val displayName: String,
    val isTrackingFree: Boolean,
    private val urlTemplate: String,
) {
    DuckDuckGo("duckduckgo", "DuckDuckGo", true, "https://duckduckgo.com/?q=$QueryPlaceholder"),
    Google("google", "Google", false, "https://www.google.com/search?q=$QueryPlaceholder"),
    Bing("bing", "Bing", false, "https://www.bing.com/search?q=$QueryPlaceholder"),
    Qwant("qwant", "Qwant", true, "https://www.qwant.com/?q=$QueryPlaceholder"),
    Ecosia("ecosia", "Ecosia", false, "https://www.ecosia.org/search?q=$QueryPlaceholder"),
    BraveSearch("brave", "Brave Search", true, "https://search.brave.com/search?q=$QueryPlaceholder");

    /** URL de résultats pour [query], correctement encodée (espaces, `&`, accents…). */
    fun searchUrl(query: String): String =
        urlTemplate.replace(QueryPlaceholder, URLEncoder.encode(query.trim(), Charsets.UTF_8.name()))

    companion object {
        val Default = DuckDuckGo

        /** Moteur d'identifiant [id], ou le moteur par défaut si inconnu (valeur stockée obsolète). */
        fun fromId(id: String?): SearchEngine = entries.firstOrNull { it.id == id } ?: Default
    }
}

private const val QueryPlaceholder = "{query}"

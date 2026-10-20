package com.openctv.browser.features.search.domain

/** Ce que la saisie de l'utilisateur désigne. [url] est toujours une URL `http`/`https` à ouvrir. */
sealed interface ResolvedInput {
    val url: String

    /** Une adresse de site (`arte.tv`, `https://example.org`, `localhost:8080`, `192.168.1.10`). */
    data class Url(override val url: String) : ResolvedInput

    /** Une recherche : [url] est la page de résultats du moteur courant. */
    data class Search(val query: String, override val url: String) : ResolvedInput
}

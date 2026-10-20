package com.openctv.browser.features.search.domain

/** Suggestion de saisie, construite uniquement à partir de données locales. */
sealed interface Suggestion {
    /** Un favori correspondant : OK l'ouvre. */
    data class Favorite(val url: String, val title: String) : Suggestion

    /** Une recherche récente : OK la relance sur le moteur courant. */
    data class Recent(val query: String) : Suggestion
}

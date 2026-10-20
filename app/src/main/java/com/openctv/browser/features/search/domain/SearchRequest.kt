package com.openctv.browser.features.search.domain

/**
 * Ouverture de la saisie d'adresse / recherche par un écran (accueil, navigateur) qui l'affiche en
 * surcouche. [initialText] pré-remplit le champ (sélectionné d'un coup, ex. l'URL de la page).
 */
data class SearchRequest(
    val mode: SearchMode = SearchMode.Browse,
    val initialText: String = "",
)

/** Ce que la saisie fait de sa validation. */
enum class SearchMode {
    /** Ouvrir l'adresse ou lancer la recherche. */
    Browse,

    /** Ajouter l'adresse saisie aux favoris, puis fermer la saisie. */
    AddBookmark,
}

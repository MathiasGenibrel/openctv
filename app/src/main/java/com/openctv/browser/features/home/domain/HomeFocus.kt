package com.openctv.browser.features.home.domain

import com.openctv.browser.core.common.Resource

/** Élément focalisable de l'accueil, mémorisé pour y revenir (retour du navigateur, des réglages, de la saisie). */
sealed interface HomeFocus {
    data object Search : HomeFocus
    data object Settings : HomeFocus

    /** Ajout d'un favori : tuile « Ajouter » de la grille, ou bouton de l'état vide. */
    data object Add : HomeFocus
    data class Favorite(val url: String) : HomeFocus

    companion object {
        /**
         * Élément à focaliser quand l'accueil réapparaît : celui qui l'a quitté s'il est encore affiché,
         * sinon le focus initial (la recherche ; le bouton d'ajout quand il n'y a aucun favori).
         */
        fun restore(remembered: HomeFocus?, favorites: Resource<List<HomeFavorite>>): HomeFocus {
            val default = initial(favorites)
            return when (remembered) {
                null -> default
                Search, Settings -> remembered
                Add -> if (favorites is Resource.Loading) default else Add
                is Favorite -> {
                    val isStillShown = favorites is Resource.Success && favorites.data.any { it.url == remembered.url }
                    if (isStillShown) remembered else default
                }
            }
        }

        private fun initial(favorites: Resource<List<HomeFavorite>>): HomeFocus {
            val isEmpty = favorites is Resource.Success && favorites.data.isEmpty()
            return if (isEmpty) Add else Search
        }
    }
}

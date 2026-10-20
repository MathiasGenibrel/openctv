package com.openctv.browser.core.navigation

import androidx.compose.runtime.saveable.Saver

/**
 * Destinations de plus haut niveau, hissées dans l'activité. Volontairement minimal : l'accueil et la
 * barre d'outils s'appuient dessus sans dépendance de navigation supplémentaire.
 */
sealed interface Destination {
    data object Home : Destination
    data object Settings : Destination
    data class Browser(val url: String) : Destination

    /**
     * Écran de saisie d'adresse / recherche. [initialText] pré-remplit (et sélectionne) le champ ;
     * [origin] dit où Retour ramène : l'accueil, ou la page ouverte (alors [initialText] = son URL).
     */
    data class Search(
        val mode: SearchMode = SearchMode.Browse,
        val initialText: String = "",
        val origin: SearchOrigin = SearchOrigin.Home,
    ) : Destination {
        /** Destination à rejoindre quand la saisie est fermée sans rien valider. */
        fun backTarget(): Destination = when (origin) {
            SearchOrigin.Home -> Home
            SearchOrigin.Browser -> Browser(initialText)
        }
    }

    companion object {
        private const val HomeToken = "home"
        private const val SettingsToken = "settings"
        private const val BrowserPrefix = "browser:"
        private const val SearchPrefix = "search:"

        internal fun encode(destination: Destination): String = when (destination) {
            Home -> HomeToken
            Settings -> SettingsToken
            is Browser -> BrowserPrefix + destination.url
            is Search ->
                "$SearchPrefix${destination.mode.name}:${destination.origin.name}:${destination.initialText}"
        }

        /** Jeton inconnu (ancienne sauvegarde) : accueil. */
        internal fun decode(token: String): Destination = when {
            token == SettingsToken -> Settings
            token.startsWith(BrowserPrefix) -> Browser(token.removePrefix(BrowserPrefix))
            token.startsWith(SearchPrefix) -> decodeSearch(token.removePrefix(SearchPrefix))
            else -> Home
        }

        /** `<mode>:<origine>:<texte>` ; le texte peut contenir `:`. Jeton illisible : accueil. */
        private fun decodeSearch(payload: String): Destination {
            val parts = payload.split(':', limit = 3)
            val mode = SearchMode.entries.firstOrNull { it.name == parts.getOrNull(0) }
            val origin = SearchOrigin.entries.firstOrNull { it.name == parts.getOrNull(1) }
            val hasText = parts.size == 3
            return if (mode != null && origin != null && hasText) Search(mode, parts[2], origin) else Home
        }

        val Saver: Saver<Destination, String> = Saver(save = { encode(it) }, restore = { decode(it) })

        /**
         * Pour une destination encore inconnue (`null` = lancement en cours de résolution) : rien n'est
         * sauvegardé, la résolution reprend donc au lancement. Une fois la destination connue, elle est
         * restaurée telle quelle après une recréation de l'activité.
         */
        val NullableSaver: Saver<Destination?, String> = Saver(
            save = { destination -> destination?.let { encode(it) } },
            restore = { decode(it) },
        )
    }
}

/** Ce que la saisie fait de sa validation. */
enum class SearchMode {
    /** Ouvrir l'adresse ou lancer la recherche dans le navigateur. */
    Browse,

    /** Ajouter l'adresse saisie aux favoris, puis revenir à l'accueil. */
    AddBookmark,
}

/** Écran qui a ouvert la saisie. */
enum class SearchOrigin { Home, Browser }

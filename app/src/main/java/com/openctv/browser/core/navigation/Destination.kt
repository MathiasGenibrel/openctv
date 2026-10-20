package com.openctv.browser.core.navigation

import androidx.compose.runtime.saveable.Saver

/**
 * Destinations de plus haut niveau, hissées dans l'activité. Volontairement minimal : l'accueil et la
 * barre d'outils s'appuient dessus sans dépendance de navigation supplémentaire.
 *
 * La saisie d'adresse / recherche n'est pas une destination : c'est une surcouche de l'écran qui l'ouvre
 * (accueil ou navigateur), qui reste ainsi vivant dessous (page, historique et focus conservés).
 */
sealed interface Destination {
    data object Home : Destination
    data object Settings : Destination
    data class Browser(val url: String) : Destination

    companion object {
        private const val HomeToken = "home"
        private const val SettingsToken = "settings"
        private const val BrowserPrefix = "browser:"

        internal fun encode(destination: Destination): String = when (destination) {
            Home -> HomeToken
            Settings -> SettingsToken
            is Browser -> BrowserPrefix + destination.url
        }

        /** Jeton inconnu (ancienne sauvegarde, dont l'ex-destination « search: ») : accueil. */
        internal fun decode(token: String): Destination = when {
            token == SettingsToken -> Settings
            token.startsWith(BrowserPrefix) -> Browser(token.removePrefix(BrowserPrefix))
            else -> Home
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

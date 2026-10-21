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

    /** Historique ; [focusedEntryId] = entrée à refocaliser en y revenant (celle qui a ouvert la page quittée). */
    data class History(val focusedEntryId: Long? = null) : Destination

    /**
     * Navigateur sur [url]. [openedFromHistoryEntry] : page rouverte depuis l'écran Historique (cette entrée) ; Retour
     * depuis la première page y revient alors, sur cette entrée, au lieu de l'accueil (cf. [returnTo]).
     */
    data class Browser(val url: String, val openedFromHistoryEntry: Long? = null) : Destination {
        /** Écran où ramène Retour quand la page n'a plus d'historique. « Accueil » mène toujours à l'accueil. */
        val returnTo: Destination get() = openedFromHistoryEntry?.let(::History) ?: Home
    }

    companion object {
        private const val HomeToken = "home"
        private const val SettingsToken = "settings"
        private const val HistoryToken = "history"
        private const val HistoryEntryPrefix = "history:"
        private const val BrowserPrefix = "browser:"

        /** `browser-from-history:<id>:<url>` : l'identifiant (chiffres) précède l'adresse, qui peut contenir des `:`. */
        private const val BrowserFromHistoryPrefix = "browser-from-history:"

        internal fun encode(destination: Destination): String = when (destination) {
            Home -> HomeToken
            Settings -> SettingsToken
            is History -> destination.focusedEntryId?.let { HistoryEntryPrefix + it } ?: HistoryToken
            is Browser -> destination.openedFromHistoryEntry
                ?.let { BrowserFromHistoryPrefix + it + ":" + destination.url }
                ?: (BrowserPrefix + destination.url)
        }

        /** Jeton inconnu (ancienne sauvegarde, dont l'ex-destination « search: ») : accueil. */
        internal fun decode(token: String): Destination = when {
            token == SettingsToken -> Settings
            token == HistoryToken -> History()
            token.startsWith(HistoryEntryPrefix) -> History(token.removePrefix(HistoryEntryPrefix).toLongOrNull())
            token.startsWith(BrowserPrefix) -> Browser(token.removePrefix(BrowserPrefix))
            token.startsWith(BrowserFromHistoryPrefix) -> browserFromHistory(token.removePrefix(BrowserFromHistoryPrefix))
            else -> Home
        }

        private fun browserFromHistory(rest: String): Destination {
            val entryId = rest.substringBefore(':').toLongOrNull()
            val url = rest.substringAfter(':')
            return Browser(url, openedFromHistoryEntry = entryId)
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

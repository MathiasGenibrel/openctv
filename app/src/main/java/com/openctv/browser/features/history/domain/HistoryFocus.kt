package com.openctv.browser.features.history.domain

/** Élément de l'écran Historique qui reçoit le focus, décidé hors de la vue (le focus ne se perd jamais). */
sealed interface HistoryFocus {
    data object BackButton : HistoryFocus
    data object ClearAll : HistoryFocus
    data class Entry(val id: Long) : HistoryFocus

    companion object {
        /** Focus à l'ouverture : la première entrée ; sans historique, le bouton retour. */
        fun initial(entries: List<HistoryEntry>): HistoryFocus = entries.firstOrNull()?.let { Entry(it.id) } ?: BackButton

        /**
         * Focus après le retrait de l'entrée [removedId] de [entries] (ordre d'affichage) : l'entrée qui prend sa place,
         * sinon la précédente ; plus aucune entrée, le bouton retour.
         */
        fun afterRemoving(entries: List<HistoryEntry>, removedId: Long): HistoryFocus {
            val index = entries.indexOfFirst { it.id == removedId }
            val neighbour = entries.getOrNull(index + 1) ?: entries.getOrNull(index - 1)
            return neighbour?.let { Entry(it.id) } ?: BackButton
        }
    }
}

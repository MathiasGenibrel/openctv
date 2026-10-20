package com.openctv.browser.core.navigation

import androidx.compose.runtime.saveable.Saver

/**
 * Destinations de plus haut niveau, hissées dans l'activité. Volontairement minimal : l'accueil et la
 * barre d'outils s'appuient dessus sans dépendance de navigation supplémentaire.
 */
sealed interface Destination {
    data object Home : Destination
    data class Browser(val url: String) : Destination

    companion object {
        /** `null` = accueil, sinon l'URL du navigateur. */
        val Saver: Saver<Destination, String> = Saver(
            save = { (it as? Browser)?.url ?: "" },
            restore = { if (it.isEmpty()) Home else Browser(it) },
        )
    }
}

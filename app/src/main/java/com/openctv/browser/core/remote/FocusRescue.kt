package com.openctv.browser.core.remote

import android.view.KeyEvent
import androidx.compose.runtime.staticCompositionLocalOf

/** Écran qui sait rendre le focus à sa page quand plus rien n'est focalisé ([FocusRescue]). */
interface PageFocusRescuer {
    /** Plus aucun élément de l'écran n'a le focus : la touche suivante tomberait dans les défauts de l'Activity. */
    fun isFocusLost(): Boolean

    /** Rend le focus à la page (ou à la barre en mode interface). `false` = rien n'a été fait (surcouche, plein écran). */
    fun restoreFocus(): Boolean
}

/**
 * Filet de sécurité du focus : une touche de la télécommande que personne n'a prise parce qu'**aucun composable n'a le
 * focus** (écran d'avertissement disparu, par exemple) ne doit pas tomber dans les défauts de l'Activity (Recherche
 * ouvre l'assistant système, les chiffres sont perdus). L'Activity ([MainActivity.dispatchKeyEvent]) nous la confie
 * **après** que la hiérarchie de vues l'a laissée passer ; on rend le focus à la page et on rejoue la touche une fois.
 *
 * Ne concerne que la croix, OK et les touches de [RemoteKeyMap] : jamais Accueil, Retour, Volume, Muet, Alimentation,
 * Assistant ni touches d'applications. Recherche, si personne ne la traite, est avalée (elle ne doit jamais ouvrir
 * l'assistant tant qu'OpenCTV est au premier plan). Kotlin pur, thread principal.
 */
class FocusRescue {
    private var rescuer: PageFocusRescuer? = null

    /** Enregistre [rescuer] ; l'appel renvoyé le retire (s'il est toujours le courant). */
    fun register(rescuer: PageFocusRescuer): () -> Unit {
        this.rescuer = rescuer
        return { if (this.rescuer === rescuer) this.rescuer = null }
    }

    /**
     * Touche [keyCode] non prise par la hiérarchie de vues ([isKeyDown] : appui, sinon relâchement). [replay]
     * rejoue l'événement et renvoie s'il a été pris. `true` = consommée ; `false` = suit son chemin normal.
     */
    fun onUnhandledKey(keyCode: Int, isKeyDown: Boolean, replay: () -> Boolean): Boolean {
        if (isKeyDown && isRescuable(keyCode)) {
            val current = rescuer
            val isReplayed = current != null && current.isFocusLost() && current.restoreFocus() && replay()
            if (isReplayed) return true
        }
        return isSwallowedWhenUnhandled(keyCode)
    }

    companion object {
        private val padKeys = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
        )

        /** La croix, OK et les touches « confort » de [RemoteKeyMap] ; aucune touche système. */
        fun isRescuable(keyCode: Int): Boolean = keyCode in padKeys || RemoteKeyMap.actionFor(keyCode) != null

        /** Recherche ouvrirait l'assistant du système : si l'application ne la prend pas, personne ne la prend. */
        fun isSwallowedWhenUnhandled(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_SEARCH
    }
}

/** Fourni par l'Activity ; `null` hors application (prévisualisations, tests). */
val LocalFocusRescue = staticCompositionLocalOf<FocusRescue?> { null }

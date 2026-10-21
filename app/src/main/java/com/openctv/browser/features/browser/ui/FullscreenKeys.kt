package com.openctv.browser.features.browser.ui

import android.view.KeyEvent
import com.openctv.browser.core.webview.MediaCommand

/**
 * Touches du lecteur plein écran. YouTube web mobile n'a aucun raccourci clavier (Entrée, Espace, Lecture/Pause
 * et flèches ne font rien d'utile) et, si la page reçoit une flèche, Chromium dessine un contour de focus bleu
 * autour du lecteur : toutes ces touches sont donc **consommées** par le conteneur plein écran et traduites en
 * [MediaCommand], uniquement en plein écran. Retour n'en fait pas partie (il quitte le plein écran).
 * Un appui maintenu ne répète pas OK (pas de clignotement de la lecture) mais répète le saut.
 */
internal object FullscreenKeys {
    /** Commande du lecteur pour cet événement ; `null` = touche consommée sans effet (Haut, Bas, relâchements). */
    fun commandFor(keyCode: Int, action: Int, repeatCount: Int): MediaCommand? {
        val isDown = action == KeyEvent.ACTION_DOWN
        val isFirstDown = isDown && repeatCount == 0
        return when {
            keyCode in confirmKeys && isFirstDown -> MediaCommand.TogglePlayback
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT && isDown -> MediaCommand.SeekBack
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && isDown -> MediaCommand.SeekForward
            else -> null
        }
    }

    /** Touches que le lecteur prend pour lui : la page ne les reçoit jamais. */
    fun isPlayerKey(keyCode: Int): Boolean = keyCode in confirmKeys || keyCode in arrowKeys

    private val confirmKeys = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)
    private val arrowKeys = setOf(
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
    )
}

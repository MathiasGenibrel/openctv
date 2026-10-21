package com.openctv.browser.core.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * La touche qui ouvre une surcouche (appui long sur OK, touche Menu) se termine dedans : ses répétitions et
 * son relâchement ne doivent pas l'actionner. Les boutons Compose for TV cliquent au relâchement de OK sans
 * vérifier où l'appui a commencé : sans ce garde, relâcher OK après un appui long cliquerait le bouton qui
 * vient de recevoir le focus. Seuls les appuis commencés dans la surcouche l'atteignent. Thread principal.
 */
class OpeningKeyGuard {
    private val pressedInside = mutableSetOf<Long>()

    /** `true` = l'événement est avalé : suite (répétition, relâchement) d'un appui commencé avant l'ouverture. */
    fun swallows(keyCode: Long, isKeyDown: Boolean, repeatCount: Int): Boolean {
        val isNewPress = isKeyDown && repeatCount == 0
        if (isNewPress) {
            pressedInside += keyCode
            return false
        }
        val isPressedInside = keyCode in pressedInside
        if (!isKeyDown) pressedInside -= keyCode
        return !isPressedInside
    }
}

/** Applique [guard] aux touches de la surcouche. Retour n'est jamais avalé : il ferme la surcouche (`BackHandler`). */
fun Modifier.ignoresOpeningKey(guard: OpeningKeyGuard): Modifier = onPreviewKeyEvent { event ->
    val isBack = event.key == Key.Back
    val isKeyDown = event.type == KeyEventType.KeyDown
    !isBack && guard.swallows(event.key.keyCode, isKeyDown, event.nativeKeyEvent.repeatCount)
}

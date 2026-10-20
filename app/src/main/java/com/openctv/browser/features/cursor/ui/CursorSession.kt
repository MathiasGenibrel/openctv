package com.openctv.browser.features.cursor.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.openctv.browser.features.cursor.domain.CursorConfig
import com.openctv.browser.features.cursor.domain.CursorController
import com.openctv.browser.features.cursor.domain.CursorDirection
import com.openctv.browser.features.cursor.domain.HoverTarget
import com.openctv.browser.features.cursor.domain.PointerEvent
import kotlinx.coroutines.channels.Channel

/**
 * Pont entre les touches de la télécommande, le [CursorController] (logique pure) et la surcouche
 * [CursorOverlay] : la racine de composition en tient un par écran de navigation, branche [onKeyEvent] sur
 * les touches de la page et passe la session à la surcouche.
 *
 * @param speedFactor vitesse du réglage « Vitesse du curseur » (lue à chaque appui : un changement dans le menu
 * s'applique tout de suite).
 */
class CursorSession(
    config: CursorConfig,
    private val speedFactor: () -> Float,
    private val nowNanos: () -> Long = System::nanoTime,
) {
    val controller = CursorController(config)

    /** Le D-pad est dans la page (ni surcouche, ni erreur) : seul état où le curseur prend les touches. */
    var isActive: Boolean = false
        private set

    /** Dernier type de cible annoncé par la page. */
    internal var hoverTarget: HoverTarget = HoverTarget.None
        private set

    internal var injector: PointerInjector? = null

    /** Réveille la boucle de frames, suspendue quand tout est au repos (économie du SoC pendant une vidéo). */
    private val wakeups = Channel<Unit>(Channel.CONFLATED)

    internal suspend fun awaitWakeup() = wakeups.receive()

    internal fun wake() {
        wakeups.trySend(Unit)
    }

    internal fun setHoverTarget(target: HoverTarget) {
        if (target == hoverTarget) return
        hoverTarget = target
        wake()
    }

    internal fun setActive(active: Boolean) {
        if (active == isActive) return
        isActive = active
        if (!active) cancelInput()
        wake()
    }

    /**
     * Touches de la page : flèches = déplacement, OK = clic. `true` = consommée. Inactif : rien n'est pris, le
     * focus Compose (bouton Réessayer, barre…) garde le D-pad.
     */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!isActive) return false
        val isKeyDown = event.type == KeyEventType.KeyDown
        val isKeyUp = event.type == KeyEventType.KeyUp
        val direction = directionOf(event.key)
        return when {
            direction != null -> onDirection(direction, isKeyDown, isKeyUp)
            isOkKey(event.key) -> onOk(isKeyDown, isKeyUp)
            else -> false
        }
    }

    private fun onDirection(direction: CursorDirection, isKeyDown: Boolean, isKeyUp: Boolean): Boolean {
        if (isKeyDown) {
            controller.speedFactor = speedFactor()
            controller.press(direction, nowNanos())
        } else if (isKeyUp) {
            controller.release(direction)
        }
        wake()
        return true
    }

    private fun onOk(isKeyDown: Boolean, isKeyUp: Boolean): Boolean {
        // Les répétitions d'une touche OK maintenue ne rouvrent pas d'appui : `pressOk` ignore un appui déjà ouvert.
        val event = if (isKeyDown) controller.pressOk() else if (isKeyUp) controller.releaseOk() else null
        event?.let(::inject)
        wake()
        return true
    }

    /** Termine proprement ce qui est en cours quand le curseur n'a plus le D-pad : pas de touche ni de doigt « coincé ». */
    private fun cancelInput() {
        controller.releaseAll()
        controller.releaseOk()?.let(::inject)
    }

    internal fun inject(event: PointerEvent) {
        injector?.inject(event)
    }

    private fun directionOf(key: Key): CursorDirection? = when (key) {
        Key.DirectionLeft -> CursorDirection.Left
        Key.DirectionRight -> CursorDirection.Right
        Key.DirectionUp -> CursorDirection.Up
        Key.DirectionDown -> CursorDirection.Down
        else -> null
    }

    private fun isOkKey(key: Key): Boolean =
        key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter
}

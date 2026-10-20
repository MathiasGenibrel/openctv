package com.openctv.browser.features.cursor.ui

import com.openctv.browser.features.cursor.domain.CursorController
import com.openctv.browser.features.cursor.domain.CursorDirection
import com.openctv.browser.features.cursor.domain.HoverTarget
import com.openctv.browser.features.cursor.domain.SpringFollower
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * État animé du pointeur : un ressort par propriété (springs M3 Expressive du design system), avancés dans la
 * boucle de frames de [CursorOverlay] et lus uniquement à la phase de dessin, donc sans recomposition ni
 * allocation par frame.
 */
internal class CursorVisuals {
    /**
     * Déplacement : fast spatial, alimenté par la vitesse du point visé. Pendant le mouvement, le pointeur
     * dessiné reste **sur** le point visé (pas de traînée de position) ; le dépassement du ressort ne se voit
     * qu'à l'arrêt, et le pointeur revient se poser sur le point où partira le clic. L'utilisateur relâche la
     * flèche sur ce qu'il voit : c'est ce point-là qui est cliqué.
     */
    val x = positionSpring()
    val y = positionSpring()

    /** Morph burst -> cercle (survol cliquable), pilule (survol texte), lueur de bord : default spatial. */
    val clickable = SpringFollower(dampingRatio = 0.8f, stiffness = 380f)
    val text = SpringFollower(dampingRatio = 0.8f, stiffness = 380f)
    val edgeGlow = SpringFollower(dampingRatio = 0.8f, stiffness = 380f)

    /** Appui OK (contraction) : fast effects, sans dépassement. */
    val press = SpringFollower(dampingRatio = 1f, stiffness = 3800f)

    /** Étirement dans le sens du déplacement et fondu (inactif) : effects, sans dépassement. */
    val stretch = SpringFollower(dampingRatio = 1f, stiffness = 800f)
    val alpha = SpringFollower(dampingRatio = 1f, stiffness = 380f)

    /** Cap du déplacement, en degrés, conservé à l'arrêt pour que la traînée ne « saute » pas. */
    var angleDegrees = 0f
        private set
    var edgeDirection = CursorDirection.Down
        private set

    /** 0..1 : accélération de l'appui long (traînée plus longue, halo réduit). */
    var acceleration = 0f
        private set

    var nowNanos = 0L
        private set
    var rippleStartNanos = NONE
        private set
    var pulseStartNanos = NONE
        private set

    private var target = HoverTarget.None
    private var wasPressed = false
    private var hasPosition = false
    private var alphaTarget = 1f

    fun setVisible(isVisible: Boolean) {
        alphaTarget = if (isVisible) 1f else 0f
    }

    fun update(controller: CursorController, hover: HoverTarget, dtSeconds: Float, now: Long) {
        nowNanos = now
        if (!hasPosition) {
            x.snapTo(controller.x)
            y.snapTo(controller.y)
            alpha.snapTo(alphaTarget)
            hasPosition = true
        }
        if (hover == HoverTarget.Clickable && target != HoverTarget.Clickable) pulseStartNanos = now
        target = hover
        if (controller.isPressed && !wasPressed) rippleStartNanos = now
        wasPressed = controller.isPressed

        x.step(controller.x, dtSeconds, targetVelocity = controller.velocityX)
        y.step(controller.y, dtSeconds, targetVelocity = controller.velocityY)
        clickable.step(if (hover == HoverTarget.Clickable) 1f else 0f, dtSeconds)
        text.step(if (hover == HoverTarget.Text) 1f else 0f, dtSeconds)
        press.step(if (controller.isPressed) 1f else 0f, dtSeconds)
        alpha.step(alphaTarget, dtSeconds)

        val speed = hypot(controller.velocityX, controller.velocityY)
        stretch.step(min(1f, speed / FULL_STRETCH_SPEED), dtSeconds)
        if (speed > MIN_ANGLE_SPEED) {
            angleDegrees = Math.toDegrees(atan2(controller.velocityY, controller.velocityX).toDouble()).toFloat()
        }
        acceleration = controller.acceleration

        val edge = controller.edge
        if (edge != null) edgeDirection = edge
        edgeGlow.step(if (edge != null) 1f else 0f, dtSeconds)
    }

    /** Plus rien ne bouge : la boucle de frames peut se suspendre jusqu'au prochain réveil. */
    fun isSettled(controller: CursorController, hover: HoverTarget, now: Long): Boolean {
        val isRippling = rippleStartNanos != NONE && now - rippleStartNanos < RIPPLE_NANOS
        val isPulsing = pulseStartNanos != NONE && hover == HoverTarget.Clickable && now - pulseStartNanos < PULSE_NANOS
        return !isRippling && !isPulsing &&
            x.isSettledOn(controller.x) && y.isSettledOn(controller.y) &&
            clickable.isSettledOn(if (hover == HoverTarget.Clickable) 1f else 0f) &&
            text.isSettledOn(if (hover == HoverTarget.Text) 1f else 0f) &&
            press.isSettledOn(if (controller.isPressed) 1f else 0f) &&
            stretch.isSettledOn(0f) &&
            edgeGlow.isSettledOn(if (controller.edge != null) 1f else 0f) &&
            alpha.isSettledOn(alphaTarget) &&
            abs(controller.velocityX) + abs(controller.velocityY) == 0f
    }

    companion object {
        private fun positionSpring() = SpringFollower(
            dampingRatio = 0.6f,
            stiffness = 800f,
            settleDistance = SpringFollower.PIXEL_SETTLE_DISTANCE,
            settleVelocity = SpringFollower.PIXEL_SETTLE_VELOCITY,
        )

        const val NONE = Long.MIN_VALUE
        const val RIPPLE_NANOS = 450_000_000L
        const val PULSE_NANOS = 1_200_000_000L
        private const val FULL_STRETCH_SPEED = 1200f
        private const val MIN_ANGLE_SPEED = 20f
    }
}

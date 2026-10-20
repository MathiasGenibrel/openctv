package com.openctv.browser.features.cursor.domain

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Ressort amorti (masse 1) qui suit une cible : [value] tend vers la cible avec la raideur et l'amortissement
 * des springs M3 Expressive. Kotlin pur, sans allocation : appelé à chaque frame depuis la boucle de dessin.
 *
 * Amortissement < 1 : léger dépassement (déplacement) ; = 1 : sans dépassement (appui).
 */
class SpringFollower(
    private val dampingRatio: Float,
    private val stiffness: Float,
    initial: Float = 0f,
) {
    var value: Float = initial
        private set
    var velocity: Float = 0f
        private set

    private val damping = 2f * dampingRatio * sqrt(stiffness)

    /** Avance de [dtSeconds] vers [target] (sous-pas fixes : stable même pour une raideur de 3800). */
    fun step(target: Float, dtSeconds: Float) {
        val steps = ceil(dtSeconds / SUBSTEP_SECONDS).toInt().coerceAtLeast(1)
        val h = dtSeconds / steps
        repeat(steps) {
            val acceleration = stiffness * (target - value) - damping * velocity
            velocity += acceleration * h
            value += velocity * h
        }
    }

    fun snapTo(target: Float) {
        value = target
        velocity = 0f
    }

    /** Au repos sur [target] : plus rien à animer, la boucle de dessin peut s'arrêter. */
    fun isSettledOn(target: Float): Boolean =
        kotlin.math.abs(target - value) < SETTLE_DISTANCE && kotlin.math.abs(velocity) < SETTLE_VELOCITY

    private companion object {
        const val SUBSTEP_SECONDS = 0.004f
        const val SETTLE_DISTANCE = 0.002f
        const val SETTLE_VELOCITY = 0.02f
    }
}

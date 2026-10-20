package com.openctv.browser.features.cursor.domain

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Ressort amorti (masse 1) qui suit une cible : [value] tend vers la cible avec la raideur et l'amortissement
 * des springs M3 Expressive. Kotlin pur, sans allocation : appelé à chaque frame depuis la boucle de dessin.
 *
 * Amortissement < 1 : léger dépassement (déplacement) ; = 1 : sans dépassement (appui).
 *
 * @param settleDistance écart sous lequel le ressort est « au repos » (à l'échelle de la valeur : 0..1 pour un
 * avancement, pixels pour une position).
 * @param settleVelocity vitesse sous laquelle le ressort est « au repos », dans la même unité par seconde.
 */
class SpringFollower(
    private val dampingRatio: Float,
    private val stiffness: Float,
    initial: Float = 0f,
    private val settleDistance: Float = UNIT_SETTLE_DISTANCE,
    private val settleVelocity: Float = UNIT_SETTLE_VELOCITY,
) {
    var value: Float = initial
        private set
    var velocity: Float = 0f
        private set

    private val damping = 2f * dampingRatio * sqrt(stiffness)

    /**
     * Avance de [dtSeconds] vers [target] (sous-pas fixes : stable même pour une raideur de 3800).
     *
     * [targetVelocity] : vitesse de la cible si elle bouge. L'amortissement porte alors sur l'écart de vitesse,
     * pas sur la vitesse absolue : une cible qui file à vitesse constante est suivie **sans retard**, et le
     * caractère du ressort (dépassement) ne se voit qu'aux changements de vitesse. [target] est alors la position
     * de fin de frame : la cible est interpolée sur la frame (elle était en `target - targetVelocity × dt` au
     * début), sinon le ressort prendrait de l'avance sur elle.
     */
    fun step(target: Float, dtSeconds: Float, targetVelocity: Float = 0f) {
        val steps = ceil(dtSeconds / SUBSTEP_SECONDS).toInt().coerceAtLeast(1)
        val h = dtSeconds / steps
        repeat(steps) { i ->
            val targetNow = target - targetVelocity * (dtSeconds - i * h)
            val acceleration = stiffness * (targetNow - value) + damping * (targetVelocity - velocity)
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
        abs(target - value) < settleDistance && abs(velocity) < settleVelocity

    companion object {
        private const val SUBSTEP_SECONDS = 0.004f
        private const val UNIT_SETTLE_DISTANCE = 0.002f
        private const val UNIT_SETTLE_VELOCITY = 0.02f

        /** Repos d'un ressort de position, en pixels : un quart de pixel ne se voit pas. */
        const val PIXEL_SETTLE_DISTANCE = 0.25f
        const val PIXEL_SETTLE_VELOCITY = 2.5f
    }
}

package com.openctv.browser.features.cursor.domain

import java.util.concurrent.TimeUnit

/**
 * Réglages de mouvement du curseur, en pixels et en nanosecondes (la densité est appliquée par l'appelant :
 * le domaine ne connaît ni dp ni Android).
 *
 * @property baseSpeed vitesse à l'appui court, en px/s, avant le facteur du réglage « Vitesse du curseur ».
 * @property maxAcceleration multiplicateur atteint après [accelerationDelayNanos] + [accelerationRampNanos] d'appui.
 * @property edgeInset marge gardée entre le point visé et le bord de la page : le pointeur reste visible.
 * @property scrollSpeed vitesse constante de défilement de la page contre un bord, en px/s.
 * @property interfaceHoldNanos durée de « Haut » maintenu au bord haut d'une page déjà en haut avant d'ouvrir
 * le mode interface.
 */
data class CursorConfig(
    val baseSpeed: Float,
    val scrollSpeed: Float,
    val edgeInset: Float,
    val maxAcceleration: Float = 3f,
    val accelerationDelayNanos: Long = TimeUnit.MILLISECONDS.toNanos(400),
    val accelerationRampNanos: Long = TimeUnit.MILLISECONDS.toNanos(600),
    val interfaceHoldNanos: Long = TimeUnit.MILLISECONDS.toNanos(600),
)

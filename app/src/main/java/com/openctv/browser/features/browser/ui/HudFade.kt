package com.openctv.browser.features.browser.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private const val EXIT_MS = 200
private const val ENTER_MS = 250

/**
 * Fondu de l'interface masquée à l'inactivité : sortie 200 ms (emphasized accelerate), entrée 250 ms
 * (emphasized decelerate). L'élément reste composé : son focus est conservé. L'alpha est lu dans la couche
 * graphique, sans recomposition à chaque frame.
 */
internal fun Modifier.hudFade(isHidden: Boolean): Modifier = composed {
    val alpha by animateFloatAsState(
        targetValue = if (isHidden) 0f else 1f,
        animationSpec = if (isHidden) tween(EXIT_MS, easing = EmphasizedAccelerate) else tween(ENTER_MS, easing = EmphasizedDecelerate),
        label = "hudFade",
    )
    graphicsLayer { this.alpha = alpha }
}

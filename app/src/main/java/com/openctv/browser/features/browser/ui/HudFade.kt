package com.openctv.browser.features.browser.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private const val EXIT_MS = 200
private const val ENTER_MS = 250

/**
 * Opacité de l'interface masquée à l'inactivité : sortie 200 ms (emphasized accelerate), entrée 250 ms
 * (emphasized decelerate). Une seule animation, partagée par la barre et le message du bas.
 */
@Composable
internal fun rememberHudAlpha(isHidden: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (isHidden) 0f else 1f,
    animationSpec = if (isHidden) tween(EXIT_MS, easing = EmphasizedAccelerate) else tween(ENTER_MS, easing = EmphasizedDecelerate),
    label = "hudAlpha",
)

/**
 * Applique [alpha] ([rememberHudAlpha]) dans la couche graphique, sans recomposition à chaque frame. L'élément
 * reste composé et focalisable : son focus est conservé, et aucune touche ne lui parvient tant qu'il est masqué
 * (la touche qui réaffiche est avalée jusqu'à son relâchement, cf. `RevealKeyGuard`). Il reste aussi dans l'arbre
 * d'accessibilité : un lecteur d'écran peut encore l'annoncer, mais toute touche le réaffiche avant d'agir.
 */
internal fun Modifier.hudFade(alpha: State<Float>): Modifier = graphicsLayer { this.alpha = alpha.value }

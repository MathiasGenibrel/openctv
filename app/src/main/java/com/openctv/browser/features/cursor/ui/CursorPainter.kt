package com.openctv.browser.features.cursor.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.openctv.browser.core.designsystem.ExpressiveShape
import com.openctv.browser.core.designsystem.OpenCtvColors
import com.openctv.browser.core.designsystem.morphToCircle
import com.openctv.browser.core.designsystem.toPath
import com.openctv.browser.features.cursor.domain.CursorDirection
import kotlin.math.PI
import kotlin.math.sin

/**
 * Dessin du pointeur (design.md, « Curseur ») : soft burst 8 pointes 24 dp `primary`, contour 3 dp `onPrimary`
 * à l'extérieur, ombre, halo 40 dp à 28 %. Survol cliquable : cercle 30 dp `tertiary`, halo pulsé. Survol
 * texte : pilule 8 x 28. Appui : contraction à 0,8 + onde. Bord : flèche + lueur.
 *
 * Appelé depuis la phase de dessin : aucune allocation par frame hors tracé du morph, recalculé seulement
 * quand son avancement change (le tracé est réutilisé au repos).
 */
internal class CursorPainter {
    private val morph = ExpressiveShape.SoftBurst.morphToCircle()
    private val shapePath = Path()
    private var cachedProgress = Float.NaN
    private var cachedSize = Float.NaN

    /** Dessine [visuals] ; (originX, originY) = coin haut gauche de la surcouche en coordonnées fenêtre. */
    fun DrawScope.drawCursor(visuals: CursorVisuals, originX: Float, originY: Float) {
        val alpha = visuals.alpha.value.coerceIn(0f, 1f)
        if (alpha <= 0.01f) return

        val edge = visuals.edgeGlow.value.coerceIn(0f, 1f)
        if (edge > 0.01f) drawEdgeGlow(visuals.edgeDirection, edge * alpha)

        val cursorX = visuals.x.value - originX
        val cursorY = visuals.y.value - originY
        translate(cursorX, cursorY) {
            drawHaloAndRipple(visuals, alpha)
            if (edge > 0.01f) drawEdgeArrow(visuals.edgeDirection, edge * alpha)
            val stretch = visuals.stretch.value.coerceIn(0f, 1f)
            rotate(visuals.angleDegrees, Offset.Zero) {
                drawTrail(visuals, stretch, alpha)
                val pressScale = 1f - PRESS_SHRINK * visuals.press.value.coerceIn(0f, 1f)
                scale(pressScale * (1f + STRETCH_GAIN * stretch), pressScale * (1f - 0.15f * stretch), Offset.Zero) {
                    drawBody(visuals, alpha)
                }
            }
        }
    }

    private fun DrawScope.drawHaloAndRipple(visuals: CursorVisuals, alpha: Float) {
        val clickable = visuals.clickable.value.coerceIn(0f, 1f)
        val color = lerp(OpenCtvColors.Primary, OpenCtvColors.Tertiary, clickable)
        val reduction = 1f - HALO_ACCELERATION_CUT * visuals.acceleration
        val pulse = pulseAt(visuals)
        val haloRadius = HALO_DIAMETER.dp.toPx() / 2f * (1f + 0.18f * pulse) * reduction
        drawCircle(color.copy(alpha = HALO_ALPHA * alpha * (1f + 0.5f * pulse)), haloRadius, Offset.Zero)

        val ripple = rippleAt(visuals)
        if (ripple >= 0f) {
            val radius = (14f + 34f * ripple).dp.toPx()
            drawCircle(
                color.copy(alpha = 0.55f * (1f - ripple) * alpha),
                radius,
                Offset.Zero,
                style = Stroke(width = 4.dp.toPx()),
            )
        }
    }

    private fun DrawScope.drawTrail(visuals: CursorVisuals, stretch: Float, alpha: Float) {
        if (stretch < 0.05f) return
        val length = TRAIL_LENGTH.dp.toPx() * stretch * (1f + 0.9f * visuals.acceleration)
        drawLine(
            color = OpenCtvColors.Primary.copy(alpha = 0.32f * alpha),
            start = Offset.Zero,
            end = Offset(-length, 0f),
            strokeWidth = (BODY_DIAMETER * 0.6f).dp.toPx(),
            cap = StrokeCap.Round,
        )
    }

    private fun DrawScope.drawBody(visuals: CursorVisuals, alpha: Float) {
        val clickable = visuals.clickable.value.coerceIn(0f, 1f)
        val text = visuals.text.value.coerceIn(0f, 1f)
        val fill = lerp(OpenCtvColors.Primary, OpenCtvColors.Tertiary, clickable)
        val outline = lerp(OpenCtvColors.OnPrimary, OpenCtvColors.OnTertiary, clickable)
        val contour = OUTLINE.dp.toPx()

        val diameter = (BODY_DIAMETER + (CIRCLE_DIAMETER - BODY_DIAMETER) * clickable).dp.toPx()
        val burstAlpha = (1f - text) * alpha
        if (burstAlpha > 0.01f) {
            updateShapePath(clickable, diameter)
            translate(-diameter / 2f, -diameter / 2f) {
                // Ombre légère, puis contour extérieur (trait centré : on double sa largeur), puis remplissage.
                translate(0f, 2.dp.toPx()) { drawPath(shapePath, Color.Black.copy(alpha = 0.28f * burstAlpha)) }
                drawPath(shapePath, outline.copy(alpha = burstAlpha), style = Stroke(width = contour * 2f))
                drawPath(shapePath, fill.copy(alpha = burstAlpha))
            }
        }
        val pillAlpha = text * alpha
        if (pillAlpha > 0.01f) {
            val width = PILL_WIDTH.dp.toPx()
            val height = PILL_HEIGHT.dp.toPx()
            val topLeft = Offset(-width / 2f, -height / 2f)
            val corner = CornerRadius(width / 2f)
            drawRoundRect(outline.copy(alpha = pillAlpha), topLeft - Offset(contour, contour), Size(width + contour * 2, height + contour * 2), CornerRadius(width / 2f + contour))
            drawRoundRect(fill.copy(alpha = pillAlpha), topLeft, Size(width, height), corner)
        }
    }

    private fun updateShapePath(progress: Float, size: Float) {
        val hasChanged = progress != cachedProgress || size != cachedSize
        if (!hasChanged) return
        morph.toPath(progress, Size(size, size), shapePath)
        cachedProgress = progress
        cachedSize = size
    }

    private fun DrawScope.drawEdgeGlow(direction: CursorDirection, strength: Float) {
        val stripe = 10.dp.toPx()
        repeat(GLOW_STRIPES) { i ->
            val a = OpenCtvColors.Primary.copy(alpha = 0.32f * strength * (1f - i / GLOW_STRIPES.toFloat()))
            val offset = i * stripe
            when (direction) {
                CursorDirection.Up -> drawRect(a, Offset(0f, offset), Size(size.width, stripe))
                CursorDirection.Down -> drawRect(a, Offset(0f, size.height - offset - stripe), Size(size.width, stripe))
                CursorDirection.Left -> drawRect(a, Offset(offset, 0f), Size(stripe, size.height))
                CursorDirection.Right -> drawRect(a, Offset(size.width - offset - stripe, 0f), Size(stripe, size.height))
            }
        }
    }

    /** Chevron de direction à côté du pointeur : indique que la page va défiler tant que la flèche est tenue. */
    private fun DrawScope.drawEdgeArrow(direction: CursorDirection, strength: Float) {
        val reach = 30.dp.toPx()
        val half = 9.dp.toPx()
        val tip = Offset(direction.dx * reach, direction.dy * reach)
        val back = Offset(direction.dx * (reach - half), direction.dy * (reach - half))
        val sideA = back + Offset(-direction.dy * half, direction.dx * half)
        val sideB = back + Offset(direction.dy * half, -direction.dx * half)
        for ((color, width) in arrowStrokes) {
            drawLine(color.copy(alpha = strength), sideA, tip, width.dp.toPx(), StrokeCap.Round)
            drawLine(color.copy(alpha = strength), sideB, tip, width.dp.toPx(), StrokeCap.Round)
        }
    }

    private val arrowStrokes = listOf(OpenCtvColors.OnPrimary to 9f, OpenCtvColors.Primary to 5f)

    /** 0..1 : halo pulsé deux fois à l'entrée sur un élément cliquable. */
    private fun pulseAt(visuals: CursorVisuals): Float {
        val start = visuals.pulseStartNanos
        if (start == CursorVisuals.NONE) return 0f
        val progress = (visuals.nowNanos - start).toFloat() / CursorVisuals.PULSE_NANOS
        if (progress !in 0f..1f) return 0f
        return sin(progress * 4f * PI.toFloat()).coerceAtLeast(0f)
    }

    /** 0..1 pendant l'onde de l'appui, -1 sinon. */
    private fun rippleAt(visuals: CursorVisuals): Float {
        val start = visuals.rippleStartNanos
        if (start == CursorVisuals.NONE) return -1f
        val progress = (visuals.nowNanos - start).toFloat() / CursorVisuals.RIPPLE_NANOS
        return if (progress in 0f..1f) progress else -1f
    }

    private companion object {
        const val BODY_DIAMETER = 24f
        const val CIRCLE_DIAMETER = 30f
        const val HALO_DIAMETER = 40f
        const val HALO_ALPHA = 0.28f
        const val HALO_ACCELERATION_CUT = 0.45f
        const val OUTLINE = 3f
        const val PILL_WIDTH = 8f
        const val PILL_HEIGHT = 28f
        const val PRESS_SHRINK = 0.2f
        const val STRETCH_GAIN = 0.35f
        const val TRAIL_LENGTH = 34f
        const val GLOW_STRIPES = 5
    }
}

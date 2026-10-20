package com.openctv.browser.core.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Échelle typographique M3 (Roboto = police système sans-serif). Source unique pour les deux thèmes. */
object OpenCtvTypography {
    private fun style(size: Int, line: Int, tracking: Float, weight: FontWeight) = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.sp,
    )

    private val Regular = FontWeight.Normal
    private val Medium = FontWeight.Medium

    val DisplayLarge = style(57, 64, -0.25f, Regular)
    val DisplayMedium = style(45, 52, 0f, Regular)
    val DisplaySmall = style(36, 44, 0f, Regular)
    val HeadlineLarge = style(32, 40, 0f, Regular)
    val HeadlineMedium = style(28, 36, 0f, Regular)
    val HeadlineSmall = style(24, 32, 0f, Regular)
    val TitleLarge = style(22, 28, 0f, Regular)
    val TitleMedium = style(16, 24, 0.15f, Medium)
    val TitleSmall = style(14, 20, 0.1f, Medium)
    val BodyLarge = style(16, 24, 0.5f, Regular)
    val BodyMedium = style(14, 20, 0.25f, Regular)
    val BodySmall = style(12, 16, 0.4f, Regular)
    val LabelLarge = style(14, 20, 0.1f, Medium)
    val LabelMedium = style(12, 16, 0.5f, Medium)
    val LabelSmall = style(11, 16, 0.5f, Medium)
}

/**
 * Variante « emphasized » d'un style de l'échelle : même taille, graisse Medium (titres d'écran, de
 * panneau). S'applique aussi aux styles lus dans le thème : `MaterialTheme.typography.displaySmall.emphasized()`.
 */
fun TextStyle.emphasized(): TextStyle = copy(fontWeight = FontWeight.Medium)

package com.openctv.browser.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes as M3Shapes
import androidx.compose.material3.Typography as M3Typography
import androidx.compose.material3.darkColorScheme as m3DarkColorScheme
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Shapes as TvShapes
import androidx.tv.material3.Typography as TvTypography
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme

/**
 * Thème d'OpenCTV : un seul schéma Dark fourni aux composants `tv-material` ET `material3`
 * (ces derniers servent aux éléments M3 Expressive : indicateurs de chargement, etc.).
 * Couleurs, formes et typographie uniquement : l'overscan reste dans [OverscanSafeArea].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OpenCtvTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = OpenCtvThemeTokens.m3ColorScheme,
        typography = OpenCtvThemeTokens.m3Typography,
        shapes = OpenCtvThemeTokens.m3Shapes,
    ) {
        TvMaterialTheme(
            colorScheme = OpenCtvThemeTokens.tvColorScheme,
            typography = OpenCtvThemeTokens.tvTypography,
            shapes = OpenCtvThemeTokens.tvShapes,
            content = content,
        )
    }
}

internal object OpenCtvThemeTokens {
    private val c = OpenCtvColors
    private val t = OpenCtvTypography
    private val s = OpenCtvShapes

    val m3ColorScheme = m3DarkColorScheme(
        primary = c.Primary, onPrimary = c.OnPrimary,
        primaryContainer = c.PrimaryContainer, onPrimaryContainer = c.OnPrimaryContainer,
        inversePrimary = c.InversePrimary,
        secondary = c.Secondary, onSecondary = c.OnSecondary,
        secondaryContainer = c.SecondaryContainer, onSecondaryContainer = c.OnSecondaryContainer,
        tertiary = c.Tertiary, onTertiary = c.OnTertiary,
        tertiaryContainer = c.TertiaryContainer, onTertiaryContainer = c.OnTertiaryContainer,
        background = c.Surface, onBackground = c.OnSurface,
        surface = c.Surface, onSurface = c.OnSurface,
        surfaceVariant = c.SurfaceContainerHighest, onSurfaceVariant = c.OnSurfaceVariant,
        inverseSurface = c.InverseSurface, inverseOnSurface = c.InverseOnSurface,
        error = c.Error, onError = c.OnError,
        errorContainer = c.ErrorContainer, onErrorContainer = c.OnErrorContainer,
        outline = c.Outline, outlineVariant = c.OutlineVariant, scrim = c.Scrim,
        surfaceContainerLowest = c.SurfaceContainerLowest, surfaceContainerLow = c.SurfaceContainerLow,
        surfaceContainer = c.SurfaceContainer, surfaceContainerHigh = c.SurfaceContainerHigh,
        surfaceContainerHighest = c.SurfaceContainerHighest,
    )

    val tvColorScheme = tvDarkColorScheme(
        primary = c.Primary, onPrimary = c.OnPrimary,
        primaryContainer = c.PrimaryContainer, onPrimaryContainer = c.OnPrimaryContainer,
        inversePrimary = c.InversePrimary,
        secondary = c.Secondary, onSecondary = c.OnSecondary,
        secondaryContainer = c.SecondaryContainer, onSecondaryContainer = c.OnSecondaryContainer,
        tertiary = c.Tertiary, onTertiary = c.OnTertiary,
        tertiaryContainer = c.TertiaryContainer, onTertiaryContainer = c.OnTertiaryContainer,
        background = c.Surface, onBackground = c.OnSurface,
        surface = c.Surface, onSurface = c.OnSurface,
        surfaceVariant = c.SurfaceContainerHighest, onSurfaceVariant = c.OnSurfaceVariant,
        inverseSurface = c.InverseSurface, inverseOnSurface = c.InverseOnSurface,
        error = c.Error, onError = c.OnError,
        errorContainer = c.ErrorContainer, onErrorContainer = c.OnErrorContainer,
        scrim = c.Scrim, border = c.Outline, borderVariant = c.OutlineVariant,
    )

    val m3Typography = M3Typography(
        displayLarge = t.DisplayLarge, displayMedium = t.DisplayMedium, displaySmall = t.DisplaySmall,
        headlineLarge = t.HeadlineLarge, headlineMedium = t.HeadlineMedium, headlineSmall = t.HeadlineSmall,
        titleLarge = t.TitleLarge, titleMedium = t.TitleMedium, titleSmall = t.TitleSmall,
        bodyLarge = t.BodyLarge, bodyMedium = t.BodyMedium, bodySmall = t.BodySmall,
        labelLarge = t.LabelLarge, labelMedium = t.LabelMedium, labelSmall = t.LabelSmall,
    )

    val tvTypography = TvTypography(
        displayLarge = t.DisplayLarge, displayMedium = t.DisplayMedium, displaySmall = t.DisplaySmall,
        headlineLarge = t.HeadlineLarge, headlineMedium = t.HeadlineMedium, headlineSmall = t.HeadlineSmall,
        titleLarge = t.TitleLarge, titleMedium = t.TitleMedium, titleSmall = t.TitleSmall,
        bodyLarge = t.BodyLarge, bodyMedium = t.BodyMedium, bodySmall = t.BodySmall,
        labelLarge = t.LabelLarge, labelMedium = t.LabelMedium, labelSmall = t.LabelSmall,
    )

    val m3Shapes = M3Shapes(
        extraSmall = s.ExtraSmall, small = s.Small, medium = s.Medium, large = s.Large,
        largeIncreased = s.LargeIncreased, extraLarge = s.ExtraLarge,
    )

    val tvShapes = TvShapes(
        extraSmall = s.ExtraSmall, small = s.Small, medium = s.Medium, large = s.Large, extraLarge = s.ExtraLarge,
    )
}

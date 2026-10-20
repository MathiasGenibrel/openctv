package com.openctv.browser.core.designsystem

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DesignSystemTokensTest {

    @Test
    fun `Given both material themes When reading their color schemes Then tv and material3 share the same palette`() {
        val m3 = OpenCtvThemeTokens.m3ColorScheme
        val tv = OpenCtvThemeTokens.tvColorScheme

        assertThat(tv.primary).isEqualTo(m3.primary)
        assertThat(tv.primaryContainer).isEqualTo(m3.primaryContainer)
        assertThat(tv.secondary).isEqualTo(m3.secondary)
        assertThat(tv.tertiaryContainer).isEqualTo(m3.tertiaryContainer)
        assertThat(tv.surface).isEqualTo(m3.surface)
        assertThat(tv.onSurfaceVariant).isEqualTo(m3.onSurfaceVariant)
        assertThat(tv.error).isEqualTo(m3.error)
    }

    @Test
    fun `Given the focus ring color When reading the palette Then it is the Secondary role from the design`() {
        assertThat(OpenCtvColors.Secondary.value).isEqualTo(androidx.compose.ui.graphics.Color(0xFFCCC2DC).value)
        assertThat(OpenCtvThemeTokens.m3ColorScheme.secondary).isEqualTo(OpenCtvColors.Secondary)
    }

    @Test
    fun `Given a tv surface When building its focus ring border Then it is a 3 dp Secondary stroke 2 dp outside the element`() {
        val border = focusRingBorder(OpenCtvShapes.ExtraLarge)

        val strokeWidth = border.border.width
        val gapToElement = border.inset - strokeWidth / 2
        assertThat(strokeWidth).isEqualTo(3.dp)
        assertThat(gapToElement).isEqualTo(2.dp)
        assertThat((border.border.brush as SolidColor).value).isEqualTo(OpenCtvColors.Secondary)
        assertThat(border.shape).isEqualTo(OpenCtvShapes.ExtraLarge)
    }

    @Test
    fun `Given the typography scale When reading body and title sizes Then they match the design for 3 meter reading`() {
        assertThat(OpenCtvTypography.DisplaySmall.fontSize.value).isEqualTo(36f)
        assertThat(OpenCtvTypography.TitleMedium.fontSize.value).isEqualTo(16f)
        assertThat(OpenCtvTypography.BodySmall.fontSize.value).isEqualTo(12f)
        assertThat(OpenCtvThemeTokens.tvTypography.displaySmall).isEqualTo(OpenCtvThemeTokens.m3Typography.displaySmall)
    }

    @Test
    fun `Given favorites in order When picking monogram shapes Then each index maps to a stable shape and wraps around`() {
        val size = ExpressiveShape.Monogram.size

        assertThat(ExpressiveShape.forMonogram(0)).isEqualTo(ExpressiveShape.forMonogram(size))
        assertThat(ExpressiveShape.forMonogram(1)).isEqualTo(ExpressiveShape.forMonogram(1))
        assertThat(ExpressiveShape.forMonogram(0)).isNotEqualTo(ExpressiveShape.forMonogram(1))
    }

    @Test
    fun `Given a negative index When picking a monogram shape Then it still returns a valid shape`() {
        assertThat(ExpressiveShape.Monogram).contains(ExpressiveShape.forMonogram(-1))
    }
}

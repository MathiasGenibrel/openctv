package com.openctv.browser.core.designsystem

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape
import androidx.graphics.shapes.RoundedPolygon

/** Formes M3 Expressive utilisables pour monogrammes et illustrations. */
enum class ExpressiveShape {
    Cookie9, Cookie12, Clover4, Sunny, Burst, SoftBurst, Pentagon, Arch, Pill;

    companion object {
        /** Formes tournantes des monogrammes : un favori garde la même forme tant que son rang est stable. */
        val Monogram = listOf(Cookie9, Clover4, SoftBurst, Pentagon, Sunny, Arch)

        fun forMonogram(index: Int): ExpressiveShape = Monogram[Math.floorMod(index, Monogram.size)]
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun ExpressiveShape.toPolygon(): RoundedPolygon = when (this) {
    ExpressiveShape.Cookie9 -> MaterialShapes.Cookie9Sided
    ExpressiveShape.Cookie12 -> MaterialShapes.Cookie12Sided
    ExpressiveShape.Clover4 -> MaterialShapes.Clover4Leaf
    ExpressiveShape.Sunny -> MaterialShapes.Sunny
    ExpressiveShape.Burst -> MaterialShapes.Burst
    ExpressiveShape.SoftBurst -> MaterialShapes.SoftBurst
    ExpressiveShape.Pentagon -> MaterialShapes.Pentagon
    ExpressiveShape.Arch -> MaterialShapes.Arch
    ExpressiveShape.Pill -> MaterialShapes.Pill
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveShape.asShape(): Shape = toPolygon().toShape()

@Preview(device = "id:tv_1080p")
@Composable
private fun ExpressiveShapesPreview() {
    OpenCtvTheme {
        Row(modifier = Modifier.padding(32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ExpressiveShape.entries.forEach { shape ->
                Box(Modifier.size(56.dp).background(OpenCtvColors.Primary, shape.asShape()))
            }
        }
    }
}

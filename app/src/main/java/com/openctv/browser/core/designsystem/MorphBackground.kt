package com.openctv.browser.core.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle

private val CirclePolygon: RoundedPolygon = RoundedPolygon.circle(numVertices = 12).normalized()

/** Transformation de cette forme Expressive en cercle (monogramme focalisé, curseur au survol d'un lien). */
fun ExpressiveShape.morphToCircle(): Morph = Morph(toPolygon().normalized(), CirclePolygon)

/**
 * Fond en forme de [morph] à l'avancement [progress] (0 = forme de départ, 1 = forme d'arrivée).
 * [progress] est lu à la phase de dessin : l'animer redessine sans recomposer l'appelant, ce qui compte
 * dans une grille de tuiles sur le SoC de la TV.
 */
fun Modifier.morphBackground(morph: Morph, color: Color, progress: () -> Float): Modifier =
    drawBehind { drawPath(morph.toPath(progress(), size), color) }

/** Contour du morph à [progress], mis à l'échelle de [size] (les polygones sont normalisés sur 0..1). */
private fun Morph.toPath(progress: Float, size: Size): Path {
    val path = Path()
    asCubics(progress).forEachIndexed { i, cubic ->
        if (i == 0) path.moveTo(cubic.anchor0X * size.width, cubic.anchor0Y * size.height)
        path.cubicTo(
            cubic.control0X * size.width, cubic.control0Y * size.height,
            cubic.control1X * size.width, cubic.control1Y * size.height,
            cubic.anchor1X * size.width, cubic.anchor1Y * size.height,
        )
    }
    path.close()
    return path
}

package com.openctv.browser.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border

/** Épaisseur de l'anneau de focus M3. */
internal val FocusRingWidth: Dp = 3.dp

/** Écart entre l'élément et le bord intérieur de l'anneau. */
internal val FocusRingOffset: Dp = 2.dp

/** Distance entre le bord de l'élément et l'axe du trait (le trait est centré sur son contour). */
internal val FocusRingStrokeCenter: Dp = FocusRingOffset + FocusRingWidth / 2

/**
 * Anneau de focus pour les surfaces `tv-material` (`Surface`, `Button`, `Card`…), à passer en
 * `focusedBorder` de leur paramètre `border`. Il est dessiné au-dessus du contenu, dans le calque de la
 * surface : il suit son agrandissement au focus et n'est jamais masqué par elle.
 * Préférer cette forme à [focusRing] dès que la surface a un `scale` au focus.
 */
fun focusRingBorder(shape: Shape): Border = Border(
    border = BorderStroke(FocusRingWidth, OpenCtvColors.Secondary),
    inset = FocusRingStrokeCenter,
    shape = shape,
)

/**
 * Anneau de focus M3 : contour 3 dp `secondary`, à 2 dp à l'extérieur de l'élément, suivant [shape].
 * Pour les éléments sans `border` de focus natif (composants `material3`, champs de saisie…) ;
 * pour une surface `tv-material`, utiliser [focusRingBorder].
 * Dessiné derrière le contenu sans clip : ne jamais appliquer `clip` / `clipToBounds` sur un parent
 * sans lui laisser au moins 5 dp de marge.
 * À placer en amont du composant focalisable : réagit au focus de l'élément ou de ses enfants.
 */
fun Modifier.focusRing(shape: Shape): Modifier = this then FocusRingElement(shape)

private data class FocusRingElement(val shape: Shape) : ModifierNodeElement<FocusRingNode>() {
    override fun create() = FocusRingNode(shape)

    override fun update(node: FocusRingNode) {
        node.shape = shape
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "focusRing"
        properties["shape"] = shape
    }
}

private class FocusRingNode(var shape: Shape) : Modifier.Node(), FocusEventModifierNode, DrawModifierNode {
    private var hasFocus = false

    override fun onFocusEvent(focusState: FocusState) {
        val focusChanged = focusState.hasFocus != hasFocus
        if (focusChanged) {
            hasFocus = focusState.hasFocus
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        if (hasFocus) {
            val grow = FocusRingStrokeCenter.toPx()
            val outline = shape.createOutline(
                Size(size.width + 2 * grow, size.height + 2 * grow),
                layoutDirection,
                this,
            )
            translate(-grow, -grow) {
                drawOutline(outline, OpenCtvColors.Secondary, style = Stroke(FocusRingWidth.toPx()))
            }
        }
        drawContent()
    }
}

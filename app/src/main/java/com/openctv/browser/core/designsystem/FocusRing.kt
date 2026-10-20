package com.openctv.browser.core.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp

private val RingWidth = 3.dp
private val RingOffset = 2.dp

/**
 * Anneau de focus M3 : contour 3 dp `secondary`, à 2 dp à l'extérieur de l'élément, suivant [shape].
 * Dessiné derrière le contenu sans clip : ne jamais appliquer `clip` / `clipToBounds` sur un parent
 * sans lui laisser au moins 5 dp de marge.
 * À placer en amont du composant focalisable : réagit au focus de l'élément ou de ses enfants.
 */
fun Modifier.focusRing(shape: Shape): Modifier = composed {
    var hasFocus by remember { mutableStateOf(false) }
    onFocusChanged { hasFocus = it.hasFocus }.drawBehind {
        if (hasFocus) {
            val grow = (RingOffset + RingWidth / 2).toPx()
            val outline = shape.createOutline(
                Size(size.width + 2 * grow, size.height + 2 * grow),
                layoutDirection,
                this,
            )
            translate(-grow, -grow) {
                drawOutline(outline, OpenCtvColors.Secondary, style = Stroke(RingWidth.toPx()))
            }
        }
    }
}

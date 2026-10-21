package com.openctv.browser.core.webview

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.openctv.browser.core.zoom.PageZoom
import kotlin.math.roundToInt

/**
 * Zoom des pages, technique « mise en page agrandie puis réduite » : la WebView est mise en page à
 * `taille / facteur` (à 50 %, 3840 × 2160 px : la page voit 1920 px CSS) puis réduite au facteur
 * ([PointerAwareWebView.applyPageZoom], pivot en haut à gauche) pour tenir dans la zone laissée par l'écran.
 * À 100 %, rien de tout cela : ni modificateur de mise en page ni échelle, la WebView est rendue exactement comme
 * sans zoom.
 *
 * À placer sur l'`AndroidView` de la WebView, avec le même [zoom] que [PointerAwareWebView.applyPageZoom].
 */
fun Modifier.pageZoomLayout(zoom: PageZoom): Modifier {
    if (zoom == PageZoom.Full) return this
    val factor = zoom.factor
    return layout { measurable, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val enlarged = Constraints.fixed((width / factor).roundToInt(), (height / factor).roundToInt())
        val placeable = measurable.measure(enlarged)
        layout(width, height) { placeable.place(0, 0) }
    }
}

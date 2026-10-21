package com.openctv.browser.core.webview

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.openctv.browser.core.zoom.PageZoom
import kotlin.math.roundToInt

/**
 * Zoom des pages, technique « mise en page agrandie puis réduite » : la WebView est mise en page à
 * `taille / facteur` (à 50 %, 3840 × 2160 px : la page voit 1920 px CSS) puis réduite au facteur ([applyPageZoom],
 * pivot en haut à gauche) pour tenir dans la zone laissée par l'écran. À 100 %, rien de tout cela : ni modificateur
 * de mise en page ni échelle, la WebView est rendue exactement comme sans zoom.
 *
 * À placer sur l'`AndroidView` de la WebView, avec le même [zoom] que [applyPageZoom].
 */
fun Modifier.pageZoomLayout(zoom: PageZoom): Modifier {
    val factor = zoom.factor
    if (zoom == PageZoom.Full) return this
    return layout { measurable, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val enlarged = Constraints.fixed((width / factor).roundToInt(), (height / factor).roundToInt())
        val placeable = measurable.measure(enlarged)
        layout(width, height) { placeable.place(0, 0) }
    }
}

/**
 * Réduit la WebView au facteur de [zoom] (pivot en haut à gauche). Les transformations d'une vue sont connues de
 * son parent (rectangle visible, entrées) : la page n'est rastérisée que là où elle est vue.
 */
fun PointerAwareWebView.applyPageZoom(zoom: PageZoom) {
    val factor = zoom.factor
    pageScale = factor
    if (zoom == PageZoom.Full) {
        scaleX = 1f
        scaleY = 1f
        resetPivot()
    } else {
        pivotX = 0f
        pivotY = 0f
        scaleX = factor
        scaleY = factor
    }
}

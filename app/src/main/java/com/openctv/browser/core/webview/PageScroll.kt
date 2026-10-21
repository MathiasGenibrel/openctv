package com.openctv.browser.core.webview

/** Sens d'un défilement d'une page entière ([PointerAwareWebView.scrollOneScreen]). */
enum class ScrollDirection { Up, Down }

/** Part de la hauteur visible parcourue par un défilement d'un écran : un peu moins, pour garder un repère de lecture. */
private const val PAGE_SCROLL_FRACTION = 0.9f

/** Pixels de l'écran à parcourir pour défiler d'un écran dans [direction] (négatif = vers le haut). */
internal fun pageScrollPixels(visibleHeightOnScreen: Int, direction: ScrollDirection): Int {
    val distance = (visibleHeightOnScreen * PAGE_SCROLL_FRACTION).toInt()
    return if (direction == ScrollDirection.Up) -distance else distance
}

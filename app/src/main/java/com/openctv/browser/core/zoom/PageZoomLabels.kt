package com.openctv.browser.core.zoom

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.openctv.browser.R

/** « 50 % » */
@Composable
fun PageZoom.percentLabel(): String = stringResource(R.string.zoom_percent, percent)

/** « 50 % · 1920 px » (valeur de la ligne des Réglages). */
@Composable
fun PageZoom.percentAndWidthLabel(): String = stringResource(R.string.zoom_percent_and_width, percent, pageWidth)

/** « 960 px », et « 1920 px · comme un écran de PC » à 50 % : largeur que voit la page à ce niveau. */
@Composable
fun PageZoom.widthLabel(): String {
    val width = stringResource(R.string.zoom_page_width, pageWidth)
    return if (this == PageZoom.Half) "$width · ${stringResource(R.string.zoom_like_pc)}" else width
}

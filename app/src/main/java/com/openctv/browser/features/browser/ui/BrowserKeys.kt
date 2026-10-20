package com.openctv.browser.features.browser.ui

import android.webkit.WebView
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Seul aiguillage des touches de l'écran de navigation, appelé en `onPreviewKeyEvent` à la racine de
 * l'écran (avant la WebView, la barre et les surcouches). `true` = touche consommée ; `false` = elle
 * poursuit vers l'élément focalisé (WebView, barre d'outils, surcouche).
 *
 * Les décisions reviennent au ViewModel ; ce fichier ne fait que traduire la touche et fournir l'état
 * de la WebView dont il a besoin (défilement possible).
 */
internal fun BrowserViewModel.consumesKey(event: KeyEvent, webView: WebView): Boolean {
    val isKeyDown = event.type == KeyEventType.KeyDown
    // Plein écran vidéo : OK, flèches et Menu appartiennent au lecteur ; Retour passe par BackHandler.
    val isVideoPlaying = layers.value.isFullscreen
    if (!isKeyDown || isVideoPlaying) return false
    return when (event.key) {
        Key.DirectionUp -> onDpadUp(canScrollUp = webView.canScrollVertically(-1))
        Key.DirectionDown -> onDpadDown()
        Key.Menu -> onMenuKey()
        else -> false
    }
}

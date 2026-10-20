package com.openctv.browser.features.browser.ui

import android.webkit.WebView
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.openctv.browser.features.browser.domain.KeyPhase
import com.openctv.browser.features.browser.domain.RemoteKey

/**
 * Touches de la page quand aucune surcouche n'est ouverte : le curseur virtuel (#8) les traduit en
 * déplacements et en clics. `true` = touche consommée. Reçoit aussi les relâchements (`KeyUp`).
 */
fun interface PageKeyHandler {
    fun onPageKey(event: KeyEvent): Boolean

    companion object {
        /** Sans curseur : toutes les touches vont à la WebView. */
        val None = PageKeyHandler { false }
    }
}

/**
 * Seul aiguillage des touches de l'écran de navigation, appelé en `onPreviewKeyEvent` à la racine de
 * l'écran (avant la WebView, la barre et les surcouches). `true` = touche consommée ; `false` = elle
 * poursuit vers l'élément focalisé (WebView, barre d'outils, surcouche).
 *
 * Les décisions reviennent au ViewModel ; ce fichier ne fait que traduire la touche et fournir l'état
 * de la WebView dont il a besoin (défilement possible).
 */
internal fun BrowserViewModel.consumesKey(event: KeyEvent, webView: WebView, pageKeys: PageKeyHandler): Boolean {
    // Interface masquée : la touche qui la réaffiche ne fait que ça jusqu'à son relâchement (ni déplacement ni clic
    // accidentel sous le pointeur ou sur la barre). Retour garde son rôle (BackHandler) ; toute touche relance le minuteur.
    if (onRemoteKey(event.toRemoteKey())) return true
    val currentLayers = layers.value
    // Plein écran vidéo : OK, flèches et Menu appartiennent au lecteur ; Retour passe par BackHandler.
    if (currentLayers.isFullscreen) return false
    // Page sans surcouche : le curseur (flèches, OK) passe avant la WebView ; il décide des touches qu'il prend.
    val isPageMode = !currentLayers.isInterfaceMode && !currentLayers.isOverlayOpen
    if (isPageMode && pageKeys.onPageKey(event)) return true
    val isKeyDown = event.type == KeyEventType.KeyDown
    if (!isKeyDown) return false
    return when (event.key) {
        Key.DirectionUp -> onDpadUp(canScrollUp = webView.canScrollVertically(-1))
        Key.DirectionDown -> onDpadDown()
        Key.Menu -> onMenuKey()
        else -> false
    }
}

private fun KeyEvent.toRemoteKey(): RemoteKey {
    val isKeyDown = type == KeyEventType.KeyDown
    val isRepeat = nativeKeyEvent.repeatCount > 0
    val phase = when {
        !isKeyDown -> KeyPhase.Up
        isRepeat -> KeyPhase.Repeat
        else -> KeyPhase.Down
    }
    return RemoteKey(code = key.keyCode, phase = phase, isBack = key == Key.Back)
}

package com.openctv.browser.features.browser.ui

import android.webkit.WebView
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.openctv.browser.core.remote.dispatchRemoteAction
import com.openctv.browser.core.webview.isTextFieldFocused
import com.openctv.browser.core.webview.sendEnter
import com.openctv.browser.core.webview.sendShiftTab
import com.openctv.browser.core.webview.sendTab
import com.openctv.browser.core.webview.showSoftKeyboard
import com.openctv.browser.features.browser.domain.ElementKey
import com.openctv.browser.features.browser.domain.ElementKeyAction
import com.openctv.browser.features.browser.domain.PadKey
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
 * de la WebView dont il a besoin (défilement possible, champ de saisie de la page focalisé).
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
    // Navigation par éléments : le D-pad part à la page en Tab / Maj+Tab / Entrée (le curseur est alors inactif).
    if (isPageMode && consumesElementKey(event, webView)) return true
    val isKeyDown = event.type == KeyEventType.KeyDown
    if (!isKeyDown) return false
    // Touches de la table (Chaîne, média, Recherche, chiffres…) : la WebView dit seulement si un champ de la page a le focus.
    val isRemoteActionConsumed = event.dispatchRemoteAction { action, isRepeat ->
        onRemoteAction(action, isRepeat, isPageTextFieldFocused = webView.isTextFieldFocused)
    }
    if (isRemoteActionConsumed) return true
    return when (event.key) {
        Key.DirectionUp -> onDpadUp(canScrollUp = webView.canScrollVertically(-1))
        Key.DirectionDown -> onDpadDown()
        Key.Menu -> onMenuKey()
        else -> false
    }
}

/**
 * Touche du D-pad ou OK en mode éléments : le ViewModel décide ([BrowserViewModel.onElementKey]), cette fonction envoie
 * seulement des `KeyEvent` à la [webView] (aucun JavaScript). `false` = la touche poursuit (curseur de texte d'un champ,
 * ou autre mode).
 */
private fun BrowserViewModel.consumesElementKey(event: KeyEvent, webView: WebView): Boolean {
    val pad = event.toPadKey() ?: return false
    val action = onElementKey(pad, event.toKeyPhase(), isTextFieldFocused = webView.isTextFieldFocused)
    when (action) {
        is ElementKeyAction.Send -> when (action.key) {
            ElementKey.Next -> webView.sendTab()
            ElementKey.Previous -> webView.sendShiftTab()
            ElementKey.Activate -> webView.sendEnter()
        }
        ElementKeyAction.OpenKeyboard -> webView.showSoftKeyboard()
        ElementKeyAction.Ignore -> Unit
        ElementKeyAction.OpenInterface, ElementKeyAction.PassThrough -> return false
    }
    return true
}

private fun KeyEvent.toPadKey(): PadKey? = when (key) {
    Key.DirectionUp -> PadKey.Up
    Key.DirectionDown -> PadKey.Down
    Key.DirectionLeft -> PadKey.Left
    Key.DirectionRight -> PadKey.Right
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> PadKey.Ok
    else -> null
}

private fun KeyEvent.toKeyPhase(): KeyPhase {
    val isKeyDown = type == KeyEventType.KeyDown
    val isRepeat = nativeKeyEvent.repeatCount > 0
    return when {
        !isKeyDown -> KeyPhase.Up
        isRepeat -> KeyPhase.Repeat
        else -> KeyPhase.Down
    }
}

private fun KeyEvent.toRemoteKey(): RemoteKey {
    return RemoteKey(code = key.keyCode, phase = toKeyPhase(), isBack = key == Key.Back)
}

package com.openctv.browser.features.browser.ui

import android.view.View
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import com.openctv.browser.features.browser.domain.BrowserLayers
import com.openctv.browser.features.browser.domain.PageState

/** Cibles de focus de l'écran de navigation, posées par [BrowserScreen] sur ses éléments. */
class BrowserFocus(
    val retry: FocusRequester = FocusRequester(),
    val address: FocusRequester = FocusRequester(),
    val menuButton: FocusRequester = FocusRequester(),
)

/**
 * Seul endroit qui déplace le focus quand les couches changent (cf. [BrowserLayers]) : le focus ne doit
 * jamais rester sans élément visible.
 * - Une surcouche refermée rend le focus à ce qui l'a ouverte (champ d'adresse, bouton Menu) en mode
 *   interface, à la page sinon.
 * - Un changement de mode déplace le focus entre la page et la barre.
 * - Le plein écran vidéo donne le focus au lecteur, puis le rend à la page en sortant (au menu s'il
 *   était ouvert : il le reprend lui-même en réapparaissant).
 * - La page = la WebView, ou le bouton Réessayer quand une erreur la remplace.
 */
@Composable
internal fun rememberBrowserFocus(viewModel: BrowserViewModel, webView: WebView, layers: BrowserLayers, fullscreenView: () -> View?): BrowserFocus {
    val focus = remember { BrowserFocus() }

    val focusPage = {
        val isShowingError = viewModel.pageState.value is PageState.Error
        if (isShowingError) focus.retry.requestFocus() else webView.requestFocus()
    }
    val focusToolbarOr = { toolbarTarget: FocusRequester ->
        val isInterfaceMode = viewModel.layers.value.isInterfaceMode
        if (isInterfaceMode) toolbarTarget.requestFocus() else focusPage()
    }
    // Entrée en plein écran : focus au lecteur ; sortie : retour au menu ouvert, sinon à la page (ou à la barre).
    OnChanged(layers.isFullscreen) { isFullscreen ->
        val isMenuOpen = viewModel.layers.value.isMenuOpen
        when {
            isFullscreen -> fullscreenView()?.requestFocus()
            isMenuOpen -> Unit
            else -> focusToolbarOr(focus.address)
        }
    }
    AfterClosed(layers.isAddressInputOpen) { focusToolbarOr(focus.address) }
    AfterClosed(layers.isMenuOpen) { focusToolbarOr(focus.menuButton) }
    OnChanged(layers.mode) { focusToolbarOr(focus.address) }
    // Recréation de l'activité en mode interface : le focus ne doit pas rester sans élément.
    LaunchedEffect(Unit) { if (viewModel.layers.value.isInterfaceMode) focus.address.requestFocus() }

    return focus
}

/** Appelle [action] à chaque fermeture d'une surcouche (pas à l'ouverture de l'écran). */
@Composable
private fun AfterClosed(isOpen: Boolean, action: () -> Unit) {
    OnChanged(isOpen) { nowOpen -> if (!nowOpen) action() }
}

/** Appelle [action] à chaque changement de [value], sauf à l'ouverture de l'écran. */
@Composable
private fun <T> OnChanged(value: T, action: (T) -> Unit) {
    var previous by remember { mutableStateOf(value) }
    val currentAction by rememberUpdatedState(action)
    LaunchedEffect(value) {
        val hasChanged = value != previous
        previous = value
        if (hasChanged) currentAction(value)
    }
}

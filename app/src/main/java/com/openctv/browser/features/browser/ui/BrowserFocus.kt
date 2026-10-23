package com.openctv.browser.features.browser.ui

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import com.openctv.browser.core.remote.LocalFocusRescue
import com.openctv.browser.core.remote.PageFocusRescuer
import com.openctv.browser.features.browser.domain.PageCover
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.withFrameNanos
import com.openctv.browser.features.browser.domain.BrowserLayers
import com.openctv.browser.features.browser.domain.PageState

/** Cibles de focus de l'écran de navigation, posées par [BrowserScreen] sur ses éléments. */
class BrowserFocus(
    val retry: FocusRequester = FocusRequester(),
    val address: FocusRequester = FocusRequester(),
    val menuButton: FocusRequester = FocusRequester(),
) {
    /** Un élément Compose de l'écran a le focus (mis à jour par la racine de [BrowserScreen]). */
    var hasFocus: Boolean = false
}

/**
 * Seul endroit qui déplace le focus quand les couches changent (cf. [BrowserLayers]) : le focus ne doit
 * jamais rester sans élément visible.
 * - Une surcouche refermée rend le focus à ce qui l'a ouverte (champ d'adresse, bouton Menu) en mode
 *   interface, à la page sinon.
 * - Un changement de mode déplace le focus entre la page et la barre.
 * - En sortie du plein écran vidéo, le focus revient à la page (au menu s'il était ouvert : il le reprend
 *   lui-même en réapparaissant). L'entrée est gérée par `DecorFullscreenPresenter`, qui attache le lecteur hors de
 *   Compose et lui donne le focus Android.
 * - Un écran d'erreur ou d'avertissement qui disparaît ([PageCover]) rend le focus à la page : son bouton le détenait.
 * - Filet : si plus rien n'a le focus quand une touche arrive, [FocusRescue] (Activity) le rend à la page.
 * - La page = la WebView, ou le bouton Réessayer quand une erreur la remplace.
 */
@Composable
internal fun rememberBrowserFocus(viewModel: BrowserViewModel, webView: WebView, layers: BrowserLayers): BrowserFocus {
    val focus = remember { BrowserFocus() }

    val focusPage = {
        val isShowingError = viewModel.pageState.value is PageState.Error
        if (isShowingError) focus.retry.requestFocus() else webView.requestFocus()
    }
    val focusToolbarOr = { toolbarTarget: FocusRequester ->
        val isInterfaceMode = viewModel.layers.value.isInterfaceMode
        if (isInterfaceMode) toolbarTarget.requestFocus() else focusPage()
    }
    // « Retour en sécurité », Retour ou Réessayer : l'écran qui détenait le focus disparaît, la page le reprend.
    // Après un cadre : la WebView redevient visible (donc focalisable) dans la même recomposition.
    val isPageCovered = PageCover.isCovering(viewModel.pageState.collectAsState().value)
    var wasPageCovered by remember { mutableStateOf(isPageCovered) }
    LaunchedEffect(isPageCovered) {
        val shouldRestore = PageCover.shouldRestoreFocus(wasPageCovered, isPageCovered)
        wasPageCovered = isPageCovered
        if (shouldRestore) {
            withFrameNanos { }
            focusToolbarOr(focus.address)
        }
    }
    // Sortie du plein écran : retour au menu ouvert, sinon à la page (ou à la barre).
    AfterClosed(layers.isFullscreen) {
        val isMenuOpen = viewModel.layers.value.isMenuOpen
        if (!isMenuOpen) focusToolbarOr(focus.address)
    }
    AfterClosed(layers.isAddressInputOpen) { focusToolbarOr(focus.address) }
    // « Historique » ferme le menu en ouvrant l'écran Historique : celui-ci prend le focus, le menu ne le rend pas.
    AfterClosed(layers.isMenuOpen) {
        val isHistoryOpen = viewModel.layers.value.isHistoryOpen
        if (!isHistoryOpen) focusToolbarOr(focus.menuButton)
    }
    AfterClosed(layers.isHistoryOpen) { focusToolbarOr(focus.menuButton) }
    OnChanged(layers.mode) { focusToolbarOr(focus.address) }
    // Recréation de l'activité en mode interface : le focus ne doit pas rester sans élément.
    LaunchedEffect(Unit) { if (viewModel.layers.value.isInterfaceMode) focus.address.requestFocus() }

    val rescue = LocalFocusRescue.current
    if (rescue != null) {
        DisposableEffect(rescue) {
            val rescuer = object : PageFocusRescuer {
                override fun isFocusLost(): Boolean = !focus.hasFocus

                override fun restoreFocus(): Boolean {
                    val current = viewModel.layers.value
                    val isHandledByOverlay = current.isOverlayOpen
                    if (isHandledByOverlay) return false
                    focusToolbarOr(focus.address)
                    return true
                }
            }
            onDispose(rescue.register(rescuer))
        }
    }

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

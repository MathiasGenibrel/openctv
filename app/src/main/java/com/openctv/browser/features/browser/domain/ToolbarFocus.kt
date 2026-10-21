package com.openctv.browser.features.browser.domain

/** Boutons de la barre d'outils susceptibles de devenir inactifs pendant qu'ils ont le focus. */
enum class ToolbarButton { Back, Forward, Reload }

/**
 * Où mettre le focus quand [focused] devient inactif : le voisin actif (Précédent <-> Suivant), sinon
 * Actualiser (toujours actif). `null` si [focused] est toujours actif : le focus ne bouge pas.
 */
fun focusFallbackOf(focused: ToolbarButton?, state: ToolbarState): ToolbarButton? {
    val isBackFocusedAndDisabled = focused == ToolbarButton.Back && !state.canGoBack
    val isForwardFocusedAndDisabled = focused == ToolbarButton.Forward && !state.canGoForward
    return when {
        isBackFocusedAndDisabled -> if (state.canGoForward) ToolbarButton.Forward else ToolbarButton.Reload
        isForwardFocusedAndDisabled -> if (state.canGoBack) ToolbarButton.Back else ToolbarButton.Reload
        else -> null
    }
}

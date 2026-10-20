package com.openctv.browser.features.browser.domain

import com.openctv.browser.features.search.domain.SearchRequest

/**
 * Ce qui recouvre la page, de la couche la plus haute à la plus basse : plein écran vidéo > saisie d'adresse > menu >
 * mode interface > page. La couche la plus haute capte Retour ([backAction]) et le D-pad avant les autres.
 *
 * Une seule valeur pour toutes les couches : chaque transition est atomique, l'écran ne voit jamais
 * d'état intermédiaire (ex. saisie fermée mais mode interface pas encore quitté).
 */
data class BrowserLayers(
    /** Saisie d'adresse ouverte en surcouche (`null` = fermée). */
    val addressInput: SearchRequest? = null,
    /** Menu ouvert en surcouche (contenu : #10). */
    val isMenuOpen: Boolean = false,
    /** Où va le D-pad quand aucune surcouche n'est ouverte. */
    val mode: BrowserMode = BrowserMode.Page,
    /** Vidéo en plein écran : couche la plus haute, elle reçoit toutes les touches (hors Retour). */
    val isFullscreen: Boolean = false,
) {
    val isAddressInputOpen: Boolean get() = addressInput != null

    /** Une surcouche modale a le focus : les touches de bascule page ↔ interface lui sont laissées. */
    val isOverlayOpen: Boolean get() = isAddressInputOpen || isMenuOpen || isFullscreen

    val isInterfaceMode: Boolean get() = mode == BrowserMode.Interface
}

/** Effet de la touche Retour, décidé par [backAction]. */
enum class BackAction { ExitFullscreen, CloseAddressInput, CloseMenu, ExitInterface, GoBackInHistory, LeaveBrowser }

/**
 * Retour retire la couche la plus haute ; sur la page seule, il recule dans l'historique, puis revient
 * à l'accueil. Jamais de sortie de l'application depuis le navigateur.
 */
fun BrowserLayers.backAction(canGoBack: Boolean): BackAction = when {
    isFullscreen -> BackAction.ExitFullscreen
    isAddressInputOpen -> BackAction.CloseAddressInput
    isMenuOpen -> BackAction.CloseMenu
    isInterfaceMode -> BackAction.ExitInterface
    canGoBack -> BackAction.GoBackInHistory
    else -> BackAction.LeaveBrowser
}

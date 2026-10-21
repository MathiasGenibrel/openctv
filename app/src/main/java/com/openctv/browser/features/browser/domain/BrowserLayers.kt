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
    /** Panneau « Options avancées » déplié sur un avertissement de sécurité : Retour le replie d'abord. */
    val isWarningOptionsOpen: Boolean = false,
) {
    val isAddressInputOpen: Boolean get() = addressInput != null

    /** Une surcouche modale a le focus : les touches de bascule page ↔ interface lui sont laissées. */
    val isOverlayOpen: Boolean get() = isAddressInputOpen || isMenuOpen || isFullscreen

    val isInterfaceMode: Boolean get() = mode == BrowserMode.Interface
}

/** Effet de la touche Retour, décidé par [backAction]. */
enum class BackAction {
    ExitFullscreen, CloseAddressInput, CloseMenu, ExitInterface,

    /** Avertissement de sécurité aux options avancées dépliées : on les replie, sans quitter l'avertissement. */
    CollapseWarningOptions,

    /** Écran d'erreur dont la page n'est pas dans l'historique : on retrouve la page affichée avant, sans reculer. */
    DismissError,
    GoBackInHistory, LeaveBrowser,
}

/**
 * Page que la WebView affiche encore sous un écran d'erreur, parce que la page en échec n'est pas entrée dans son
 * historique : Retour la ré-affiche ([BackAction.DismissError]) au lieu de reculer d'un cran, qui la sauterait.
 */
enum class PageUnderError {
    /** Aucun écran d'erreur, ou la page en échec est entrée dans l'historique : Retour recule normalement. */
    None,

    /**
     * Avertissement de sécurité (HTTP, certificat refusé) : la navigation a été refusée avant d'aboutir, la page
     * d'avant est intacte, même quand elle est la première de l'historique.
     */
    BehindSecurityWarning,

    /** Erreur de chargement ordinaire : la page d'avant n'est ré-affichée que si l'historique remonte au-delà. */
    BehindLoadError,
}

/**
 * Retour retire la couche la plus haute ; sur la page seule, il ré-affiche la page restée sous un écran d'erreur
 * ([pageUnderError]), recule dans l'historique, puis revient à l'accueil. Jamais de sortie de l'application depuis
 * le navigateur.
 */
fun BrowserLayers.backAction(canGoBack: Boolean, pageUnderError: PageUnderError = PageUnderError.None): BackAction {
    val isPreviousPageShownAgain = when (pageUnderError) {
        PageUnderError.None -> false
        PageUnderError.BehindSecurityWarning -> true
        PageUnderError.BehindLoadError -> canGoBack
    }
    return when {
        isFullscreen -> BackAction.ExitFullscreen
        isAddressInputOpen -> BackAction.CloseAddressInput
        isMenuOpen -> BackAction.CloseMenu
        isInterfaceMode -> BackAction.ExitInterface
        isWarningOptionsOpen -> BackAction.CollapseWarningOptions
        isPreviousPageShownAgain -> BackAction.DismissError
        canGoBack -> BackAction.GoBackInHistory
        else -> BackAction.LeaveBrowser
    }
}

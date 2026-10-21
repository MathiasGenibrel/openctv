package com.openctv.browser.features.browser.domain

import com.openctv.browser.core.navmode.NavigationMode
import com.openctv.browser.core.zoom.PageZoom

/** Interface affichée par-dessus la page (barre d'outils, aide) ; calculée hors de la vue, voir [browserHudOf]. */
data class BrowserHud(
    /** Barre affichée : en mode interface, et pendant un chargement (elle porte la progression). */
    val isToolbarShown: Boolean,
    /** La barre peut prendre le focus (mode interface) ; sinon elle s'affiche sans jamais le prendre. */
    val isToolbarInteractive: Boolean,
    /** Snackbar « Mode interface : … », masquée sous une surcouche. */
    val isInterfaceHelpShown: Boolean,
    /** Snackbar « Mode curseur : … » + astuce « Maintenir Haut », quelques secondes en mode page. */
    val isCursorHelpShown: Boolean = false,
    /** Le D-pad est dans la page : le curseur virtuel prend les touches et s'affiche (jamais en plein écran, sous une surcouche ou sur une erreur). */
    val isCursorVisible: Boolean = false,
    /**
     * Interface masquée après une inactivité (fondu) : barre, aides et pointeur disparaissent, mais restent
     * en place (focus et position du pointeur conservés). Jamais sous une surcouche, pendant un chargement,
     * sur une erreur, pendant qu'un message bref ([BrowserNotice]) est affiché, ni en plein écran.
     */
    val isHidden: Boolean = false,
    /** Le pointeur est dessiné : [isCursorVisible] et interface non masquée. Il garde les touches même masqué. */
    val isCursorShown: Boolean = false,
    /** Snackbar « Mode éléments : … » + astuce « Maintenir Haut », quelques secondes en mode page éléments. */
    val isElementsHelpShown: Boolean = false,
)

fun browserHudOf(
    layers: BrowserLayers,
    pageState: PageState,
    isCursorHelpRequested: Boolean = false,
    isIdle: Boolean = false,
    isNoticeShown: Boolean = false,
    navigationMode: NavigationMode = NavigationMode.Cursor,
): BrowserHud {
    // Vidéo plein écran : aucune interface OpenCTV par-dessus le lecteur.
    if (layers.isFullscreen) return BrowserHud(isToolbarShown = false, isToolbarInteractive = false, isInterfaceHelpShown = false)
    val isLoading = pageState is PageState.Loading
    val isPageFocused = !layers.isInterfaceMode && !layers.isOverlayOpen && pageState !is PageState.Error
    // Le D-pad est dans la page : au curseur, le pointeur le prend ; aux éléments, il part à la page en Tab (pas de pointeur).
    val isCursorVisible = isPageFocused && navigationMode == NavigationMode.Cursor
    val isElementsActive = isPageFocused && navigationMode == NavigationMode.Elements
    val isError = pageState is PageState.Error
    // Un message bref reste lisible toute sa durée : le masquage attend qu'il s'efface.
    val canHide = !layers.isOverlayOpen && !isLoading && !isError && !isNoticeShown
    val isHidden = isIdle && canHide
    return BrowserHud(
        isToolbarShown = layers.isInterfaceMode || isLoading,
        isToolbarInteractive = layers.isInterfaceMode,
        isInterfaceHelpShown = layers.isInterfaceMode && !layers.isOverlayOpen,
        isCursorHelpShown = isCursorHelpRequested && isCursorVisible,
        isElementsHelpShown = isCursorHelpRequested && isElementsActive,
        isCursorVisible = isCursorVisible,
        isHidden = isHidden,
        isCursorShown = isCursorVisible && !isHidden,
    )
}

/** Message bref affiché en bas de l'écran, sans prendre le focus (cf. `BrowserViewModel.notice`). */
sealed interface BrowserNotice {
    data object BookmarkFailed : BrowserNotice
    data object AdBlockFailed : BrowserNotice
    data object SettingFailed : BrowserNotice

    /** Page HTTP affichée par exception : ne rien y saisir de sensible. */
    data object InsecureHttpShown : BrowserNotice

    /** Page affichée malgré un certificat invalide accordé par l'utilisateur. */
    data object UnverifiedCertificateShown : BrowserNotice

    /** Le choix « toujours autoriser » n'a pas pu être mémorisé : il ne vaut que pour la session. */
    data object SecurityExceptionNotSaved : BrowserNotice

    /** Mode [mode] mémorisé pour [host] (hôte exact) : la page l'applique tout de suite. */
    data class NavigationModeChanged(val mode: NavigationMode, val host: String) : BrowserNotice

    /** Zoom [zoom] mémorisé pour [host] (hôte exact). */
    data class ZoomSaved(val zoom: PageZoom, val host: String) : BrowserNotice

    /** [host] revient au zoom par défaut [zoom] : son zoom propre est retiré. */
    data class ZoomReset(val zoom: PageZoom, val host: String) : BrowserNotice
}

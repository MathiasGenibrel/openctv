package com.openctv.browser.features.browser.domain

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
)

fun browserHudOf(
    layers: BrowserLayers,
    pageState: PageState,
    isCursorHelpRequested: Boolean = false,
    isIdle: Boolean = false,
    isNoticeShown: Boolean = false,
): BrowserHud {
    // Vidéo plein écran : aucune interface OpenCTV par-dessus le lecteur.
    if (layers.isFullscreen) return BrowserHud(isToolbarShown = false, isToolbarInteractive = false, isInterfaceHelpShown = false)
    val isLoading = pageState is PageState.Loading
    val isCursorVisible = !layers.isInterfaceMode && !layers.isOverlayOpen && pageState !is PageState.Error
    val isError = pageState is PageState.Error
    // Un message bref reste lisible toute sa durée : le masquage attend qu'il s'efface.
    val canHide = !layers.isOverlayOpen && !isLoading && !isError && !isNoticeShown
    val isHidden = isIdle && canHide
    return BrowserHud(
        isToolbarShown = layers.isInterfaceMode || isLoading,
        isToolbarInteractive = layers.isInterfaceMode,
        isInterfaceHelpShown = layers.isInterfaceMode && !layers.isOverlayOpen,
        isCursorHelpShown = isCursorHelpRequested && isCursorVisible,
        isCursorVisible = isCursorVisible,
        isHidden = isHidden,
        isCursorShown = isCursorVisible && !isHidden,
    )
}

/** Message bref affiché en bas de l'écran, sans prendre le focus (cf. `BrowserViewModel.notice`). */
enum class BrowserNotice { BookmarkFailed, AdBlockFailed, SettingFailed }

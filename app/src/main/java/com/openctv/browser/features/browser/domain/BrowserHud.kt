package com.openctv.browser.features.browser.domain

/** Interface affichée par-dessus la page (barre d'outils, aide) ; calculée hors de la vue, voir [browserHudOf]. */
data class BrowserHud(
    /** Barre affichée : en mode interface, et pendant un chargement (elle porte la progression). */
    val isToolbarShown: Boolean,
    /** La barre peut prendre le focus (mode interface) ; sinon elle s'affiche sans jamais le prendre. */
    val isToolbarInteractive: Boolean,
    /** Snackbar « Mode interface : … », masquée sous une surcouche. */
    val isInterfaceHelpShown: Boolean,
)

fun browserHudOf(layers: BrowserLayers, pageState: PageState): BrowserHud {
    // Vidéo plein écran : aucune interface OpenCTV par-dessus le lecteur.
    if (layers.isFullscreen) return BrowserHud(isToolbarShown = false, isToolbarInteractive = false, isInterfaceHelpShown = false)
    val isLoading = pageState is PageState.Loading
    return BrowserHud(
        isToolbarShown = layers.isInterfaceMode || isLoading,
        isToolbarInteractive = layers.isInterfaceMode,
        isInterfaceHelpShown = layers.isInterfaceMode && !layers.isOverlayOpen,
    )
}

/** Message bref affiché en bas de l'écran, sans prendre le focus (cf. `BrowserViewModel.notice`). */
enum class BrowserNotice { BookmarkFailed, AdBlockFailed, SettingFailed }

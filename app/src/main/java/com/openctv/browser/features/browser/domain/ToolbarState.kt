package com.openctv.browser.features.browser.domain

/** Action du bouton Actualiser : devient Arrêter pendant le chargement. */
enum class ReloadAction { Reload, Stop }

/** Ce que la barre d'outils affiche ; calculé hors de la vue, voir [toolbarStateOf]. */
data class ToolbarState(
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val reloadAction: ReloadAction,
    /** Domaine lisible de la page (sans `www.`). */
    val site: String,
    /** Page en https : le cadenas est affiché. */
    val isSecure: Boolean,
    val isBookmarked: Boolean,
)

fun toolbarStateOf(
    pageState: PageState,
    pageUrl: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isBookmarked: Boolean,
): ToolbarState {
    val isLoading = pageState is PageState.Loading
    // Une page refusée (certificat, réseau) n'est pas affichée : jamais de cadenas sur son adresse.
    val isPageShown = pageState !is PageState.Error
    return ToolbarState(
        canGoBack = canGoBack,
        canGoForward = canGoForward,
        reloadAction = if (isLoading) ReloadAction.Stop else ReloadAction.Reload,
        site = siteLabelOf(pageUrl),
        isSecure = isPageShown && pageUrl.trim().startsWith("https://", ignoreCase = true),
        isBookmarked = isBookmarked,
    )
}

package com.openctv.browser.features.browser.domain

import com.openctv.browser.core.security.SiteHost

/** Action du bouton Actualiser : devient Arrêter pendant le chargement. */
enum class ReloadAction { Reload, Stop }

/** Pourquoi une page affichée n'est pas vraiment sûre (affiché dans la barre à la place du cadenas). */
enum class PageInsecurity {
    /** Page en HTTP, affichée par exception. */
    Http,

    /** Page HTTPS dont le certificat est invalide, affichée par exception. */
    UnverifiedCertificate,
}

/** Ce que la barre d'outils affiche ; calculé hors de la vue, voir [toolbarStateOf]. */
data class ToolbarState(
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val reloadAction: ReloadAction,
    /** Domaine lisible de la page (sans `www.`). */
    val site: String,
    /** Page en https, certificat valide : le cadenas est affiché. */
    val isSecure: Boolean,
    val isBookmarked: Boolean,
    /** Page affichée par exception (HTTP, certificat non vérifié) : « Non sécurisé » / « Certificat non vérifié ». */
    val insecurity: PageInsecurity? = null,
)

/**
 * [unverifiedCertificateHost] : hôte dont la page principale a été acceptée malgré un certificat invalide
 * (exception de l'utilisateur), `null` si aucun.
 */
fun toolbarStateOf(
    pageState: PageState,
    pageUrl: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isBookmarked: Boolean,
    unverifiedCertificateHost: String? = null,
): ToolbarState {
    val isLoading = pageState is PageState.Loading
    // Une page refusée (certificat, réseau) n'est pas affichée : jamais de cadenas sur son adresse.
    val isPageShown = pageState !is PageState.Error
    val isHttps = pageUrl.trim().startsWith("https://", ignoreCase = true)
    val isHttp = pageUrl.trim().startsWith("http://", ignoreCase = true)
    val isCertificateUnverified = isHttps && unverifiedCertificateHost != null && SiteHost.of(pageUrl) == unverifiedCertificateHost
    val insecurity = when {
        !isPageShown -> null
        isHttp -> PageInsecurity.Http
        isCertificateUnverified -> PageInsecurity.UnverifiedCertificate
        else -> null
    }
    return ToolbarState(
        canGoBack = canGoBack,
        canGoForward = canGoForward,
        reloadAction = if (isLoading) ReloadAction.Stop else ReloadAction.Reload,
        site = siteLabelOf(pageUrl),
        isSecure = isPageShown && isHttps && !isCertificateUnverified,
        isBookmarked = isBookmarked,
        insecurity = insecurity,
    )
}

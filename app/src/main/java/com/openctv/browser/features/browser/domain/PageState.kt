package com.openctv.browser.features.browser.domain

import com.openctv.browser.core.webview.LoadErrorCause

/** État de la page affichée par l'écran de navigation. [site] est le nom lisible du site (sans `www.`). */
sealed interface PageState {
    val site: String

    /**
     * [isPageShown] : une page est déjà affichée derrière ce chargement (lien suivi dans la page) ;
     * seule la barre de progression le signale. Sinon (premier chargement, après une erreur), un
     * indicateur plein écran masque la WebView vierge.
     */
    data class Loading(
        override val site: String,
        val progress: Float,
        val isPageShown: Boolean = false,
    ) : PageState

    data class Loaded(override val site: String) : PageState
    data class Error(override val site: String, val cause: LoadErrorCause) : PageState
}

/** `https://www.arte.tv/fr/?x=1` -> `arte.tv`. Retombe sur la saisie brute si aucun hôte n'est lisible. */
fun siteLabelOf(url: String): String {
    val withoutScheme = url.trim().substringAfter("://")
    val authority = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    val host = authority.substringAfterLast('@').substringBefore(':').removePrefix("www.")
    return host.ifEmpty { url }
}

package com.openctv.browser.features.browser.domain

import com.openctv.browser.core.webview.LoadErrorCause
import com.openctv.browser.features.bookmarks.domain.BookmarkUrl

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

/** `https://www.arte.tv/fr/?x=1` -> `arte.tv` : même libellé que le titre d'un favori ([BookmarkUrl.siteName]). */
fun siteLabelOf(url: String): String = BookmarkUrl.siteName(url)

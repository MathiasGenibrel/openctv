package com.openctv.browser.features.browser.domain

import com.openctv.browser.core.webview.LoadErrorCause
import com.openctv.browser.core.webview.SecurityWarning
import com.openctv.browser.features.bookmarks.domain.BookmarkUrl

/** État de la page affichée par l'écran de navigation. [site] est le nom lisible du site (sans `www.`). */
sealed interface PageState {
    val site: String

    /**
     * [isPageShown] : une page est déjà affichée derrière ce chargement (lien suivi dans la page, ou premier
     * rendu visible de la page chargée, `onPageCommitVisible`) ; seule la barre de progression le signale.
     * Sinon (premier chargement, après une erreur), un indicateur plein écran masque la WebView vierge.
     */
    data class Loading(
        override val site: String,
        val progress: Float,
        val isPageShown: Boolean = false,
    ) : PageState

    data class Loaded(override val site: String) : PageState
    /**
     * Page non affichée. [warning] non nul : avertissement de sécurité (HTTP, certificat invalide) que
     * l'utilisateur peut contourner ; sinon écran d'erreur ordinaire. Dans les deux cas la page est « refusée » :
     * pas de curseur, pas de masquage de l'interface, Retour ré-affiche la page d'avant.
     */
    data class Error(
        override val site: String,
        val cause: LoadErrorCause,
        val warning: SecurityWarning? = null,
    ) : PageState
}

/** `https://www.arte.tv/fr/?x=1` -> `arte.tv` : même libellé que le titre d'un favori ([BookmarkUrl.siteName]). */
fun siteLabelOf(url: String): String = BookmarkUrl.siteName(url)

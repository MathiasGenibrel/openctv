package com.openctv.browser.features.browser.domain

/**
 * Règle du focus quand un écran d'erreur ou d'avertissement recouvre la page. Il détient le focus (son bouton) ; en
 * disparaissant, il ne le donne à personne : sans l'étape suivante, plus aucun élément n'est focalisé, le curseur est
 * invisible et les touches (Recherche, chiffres) ne passent plus par le navigateur.
 */
object PageCover {
    /** L'écran d'erreur (avertissement de sécurité compris) recouvre la page. */
    fun isCovering(state: PageState): Boolean = state is PageState.Error

    /** La page (ou la barre) doit reprendre le focus : l'écran qui le détenait vient de disparaître. */
    fun shouldRestoreFocus(wasCovering: Boolean, isCovering: Boolean): Boolean = wasCovering && !isCovering
}

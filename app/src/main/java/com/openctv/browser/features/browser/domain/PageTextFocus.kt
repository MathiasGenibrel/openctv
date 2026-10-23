package com.openctv.browser.features.browser.domain

/**
 * Décide si un champ de saisie de la page attend du texte, à partir de ce que dit le moteur (`onCheckIsTextEditor`,
 * état d'IME) et de ce que l'application sait de la page. Kotlin pur ; aucun JavaScript.
 *
 * Le moteur garde son état d'IME « éditeur de texte » quand un avertissement de sécurité (ou une erreur) recouvre la
 * page puis disparaît : la WebView croit toujours qu'un champ est focalisé alors qu'aucun ne l'est, et chiffres,
 * Recherche et Chaîne seraient cédés à une page qui ne les attend pas. Cet état est donc **périmé** après un écran
 * d'erreur ou d'avertissement, jusqu'à ce que l'utilisateur agisse sur la page (nouvelle page, clic, OK en mode
 * éléments) : seul un geste peut lui redonner un vrai champ focalisé. Thread principal.
 */
class PageTextFocus {
    private var isEngineStateStale = false

    /** Un écran d'erreur ou d'avertissement recouvre la page : l'état d'IME du moteur n'est plus fiable. */
    fun onPageCovered() {
        isEngineStateStale = true
    }

    /** Une nouvelle page se charge, ou l'utilisateur agit sur la page : le moteur redit la vérité. */
    fun onPageInteraction() {
        isEngineStateStale = false
    }

    /** [isEditorFocusedInEngine] : réponse du moteur ; [isPageDisplayed] : une page (pas une erreur) est affichée. */
    fun isTextFieldFocused(isEditorFocusedInEngine: Boolean, isPageDisplayed: Boolean): Boolean {
        val isTrusted = isPageDisplayed && !isEngineStateStale
        return isEditorFocusedInEngine && isTrusted
    }

    /**
     * Le clavier est réellement affiché sur un champ de la page : la même lecture que [isTextFieldFocused] (une seule
     * source, l'état d'IME du moteur) et le clavier système visible ([isKeyboardShownBySystem]). Un champ focalisé sans
     * clavier (Tab l'a atteint, Retour l'a fermé) n'est pas « en saisie ».
     */
    fun isKeyboardShown(isEditorFocusedInEngine: Boolean, isKeyboardShownBySystem: Boolean, isPageDisplayed: Boolean): Boolean =
        isKeyboardShownBySystem && isTextFieldFocused(isEditorFocusedInEngine, isPageDisplayed)
}

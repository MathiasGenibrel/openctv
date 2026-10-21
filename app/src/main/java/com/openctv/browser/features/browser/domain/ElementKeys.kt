package com.openctv.browser.features.browser.domain

/** Touche de la croix directionnelle ou OK, vue par la navigation par éléments. */
enum class PadKey { Up, Down, Left, Right, Ok }

/** Touche de clavier envoyée à la page : Tab, Maj+Tab ou Entrée. */
enum class ElementKey { Next, Previous, Activate }

/** Ce que la navigation par éléments fait d'une touche du D-pad (cf. [ElementKeys]). */
sealed interface ElementKeyAction {
    /** Envoie [key] à la WebView (appui puis relâchement). */
    data class Send(val key: ElementKey) : ElementKeyAction

    /** Un champ de saisie est focalisé : OK ouvre le clavier système. */
    data object OpenKeyboard : ElementKeyAction

    /** Haut maintenu : ouvre la barre d'adresse. */
    data object OpenInterface : ElementKeyAction

    /** Touche prise sans rien faire (répétition d'OK, relâchement d'une touche déjà traitée). */
    data object Ignore : ElementKeyAction

    /** Touche laissée à la WebView (Gauche/Droite dans un champ de saisie : déplacement du curseur de texte). */
    data object PassThrough : ElementKeyAction
}

/**
 * Table unique de traduction D-pad → clavier du mode éléments : Bas/Droite = Tab, Haut/Gauche = Maj+Tab, OK = Entrée.
 * Kotlin pur ; seul l'envoi des `KeyEvent` dépend d'Android (`core/webview/ElementKeyInjector`). Aucun JavaScript.
 */
object ElementKeys {
    /**
     * Action de [key] dans [phase]. Dans un champ de saisie ([isTextFieldFocused]), Gauche/Droite restent à la page
     * (curseur de texte) et OK ouvre le clavier ; Haut/Bas continuent de changer d'élément. Haut maintenu ouvre la
     * barre : le premier appui a déjà changé d'élément, la première répétition ouvre la barre.
     */
    fun actionFor(key: PadKey, phase: KeyPhase, isTextFieldFocused: Boolean): ElementKeyAction {
        val isHorizontal = key == PadKey.Left || key == PadKey.Right
        val isLeftToTextField = isTextFieldFocused && isHorizontal
        if (isLeftToTextField) return ElementKeyAction.PassThrough
        return when (phase) {
            KeyPhase.Up -> ElementKeyAction.Ignore
            KeyPhase.Repeat -> repeatAction(key)
            KeyPhase.Down -> pressAction(key, isTextFieldFocused)
        }
    }

    private fun pressAction(key: PadKey, isTextFieldFocused: Boolean): ElementKeyAction = when (key) {
        PadKey.Down, PadKey.Right -> ElementKeyAction.Send(ElementKey.Next)
        PadKey.Up, PadKey.Left -> ElementKeyAction.Send(ElementKey.Previous)
        PadKey.Ok -> if (isTextFieldFocused) ElementKeyAction.OpenKeyboard else ElementKeyAction.Send(ElementKey.Activate)
    }

    private fun repeatAction(key: PadKey): ElementKeyAction = when (key) {
        PadKey.Up -> ElementKeyAction.OpenInterface
        PadKey.Ok -> ElementKeyAction.Ignore
        else -> pressAction(key, isTextFieldFocused = false)
    }
}

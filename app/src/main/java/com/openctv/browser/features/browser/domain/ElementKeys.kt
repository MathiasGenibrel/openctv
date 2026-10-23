package com.openctv.browser.features.browser.domain

/** Touche de la croix directionnelle ou OK, vue par la navigation par éléments. */
enum class PadKey { Up, Down, Left, Right, Ok }

/** Touche de clavier envoyée à la page : Tab, Maj+Tab ou Entrée. */
enum class ElementKey { Next, Previous, Activate }

/** Ce que la navigation par éléments fait d'une touche du D-pad (cf. [ElementKeys]). */
sealed interface ElementKeyAction {
    /** Envoie [key] à la WebView (appui puis relâchement). */
    data class Send(val key: ElementKey) : ElementKeyAction

    /** Un champ de saisie est focalisé : OK (ré)ouvre le clavier système. */
    data object OpenKeyboard : ElementKeyAction

    /** Haut maintenu : ouvre la barre d'adresse. */
    data object OpenInterface : ElementKeyAction

    /** Touche prise sans rien faire (Haut en attente de son relâchement, répétition d'OK, relâchement déjà traité). */
    data object Ignore : ElementKeyAction

    /** Touche laissée à la WebView (Gauche/Droite dans un champ de saisie : déplacement du curseur de texte). */
    data object PassThrough : ElementKeyAction
}

/**
 * Traduction D-pad → clavier du mode éléments : Bas/Droite = Tab, Haut/Gauche = Maj+Tab, OK = Entrée.
 * Kotlin pur ; seul l'envoi des `KeyEvent` dépend d'Android (`core/webview/ElementKeyInjector`). Aucun JavaScript.
 *
 * **Haut agit au relâchement** : appui court = élément précédent, appui long (première répétition) = barre d'adresse,
 * sans Maj+Tab. Sans cela, le Maj+Tab du premier appui déplacerait le focus de la page avant l'ouverture de la barre.
 * D'où le seul état retenu : un appui sur Haut en attente de son issue. Thread principal (aiguillage des touches).
 */
class ElementKeys {
    private var isUpPending = false

    /**
     * Action de [key] dans [phase]. Tab peut ouvrir le clavier sur un champ de saisie ([isTextFieldFocused]) : OK le
     * (ré)ouvre. Seul un clavier réellement affiché ([isKeyboardShown]) garde Gauche/Droite pour le curseur de texte ;
     * clavier fermé (Retour l'a fermé), toutes les flèches changent d'élément, même sur un champ qui a eu un clavier.
     * Haut/Bas changent toujours d'élément.
     */
    fun actionFor(key: PadKey, phase: KeyPhase, isTextFieldFocused: Boolean, isKeyboardShown: Boolean): ElementKeyAction {
        val isHorizontal = key == PadKey.Left || key == PadKey.Right
        val isLeftToTextField = isTextFieldFocused && isKeyboardShown && isHorizontal
        if (isLeftToTextField) return ElementKeyAction.PassThrough
        if (key == PadKey.Up) return upAction(phase)
        return when (phase) {
            KeyPhase.Down -> pressAction(key, isTextFieldFocused)
            KeyPhase.Repeat -> if (key == PadKey.Ok) ElementKeyAction.Ignore else pressAction(key, isTextFieldFocused)
            KeyPhase.Up -> ElementKeyAction.Ignore
        }
    }

    /** Haut : l'appui attend ; la première répétition ouvre la barre, sinon le relâchement passe à l'élément précédent. */
    private fun upAction(phase: KeyPhase): ElementKeyAction {
        val wasPending = isUpPending
        isUpPending = phase == KeyPhase.Down
        return when {
            phase == KeyPhase.Down -> ElementKeyAction.Ignore
            !wasPending -> ElementKeyAction.Ignore
            phase == KeyPhase.Repeat -> ElementKeyAction.OpenInterface
            else -> ElementKeyAction.Send(ElementKey.Previous)
        }
    }

    private fun pressAction(key: PadKey, isTextFieldFocused: Boolean): ElementKeyAction = when (key) {
        PadKey.Down, PadKey.Right -> ElementKeyAction.Send(ElementKey.Next)
        PadKey.Up, PadKey.Left -> ElementKeyAction.Send(ElementKey.Previous)
        PadKey.Ok -> if (isTextFieldFocused) ElementKeyAction.OpenKeyboard else ElementKeyAction.Send(ElementKey.Activate)
    }
}

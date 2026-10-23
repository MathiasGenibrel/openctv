package com.openctv.browser.core.webview

/**
 * Bouclage du mode éléments : Tab après le dernier élément de la page (Maj+Tab avant le premier) fait rendre le focus
 * au moteur (`takeFocus` → `focusSearch` de la vue), qui vide alors le focus de la page : l'appui suivant, sinon, est
 * « mort ». [armed] est appelé à l'envoi d'un Tab ; si le moteur rend le focus peu après ([onEngineTookFocus]), on
 * réinjecte **une seule fois** la même touche, qui focalise le premier (dernier) élément. Une page sans élément
 * focalisable rend le focus à la réinjection aussi, mais celle-ci n'arme rien : pas de boucle. Le moteur peut appeler
 * `focusSearch` deux fois pour un même `takeFocus` (direction gauche/droite, puis avant/arrière) : seul le premier
 * appel réinjecte.
 *
 * Toute autre touche reçue par la vue avant le retour du moteur ([onKeyDispatched]) désarme : `focusSearch` vient
 * alors d'une flèche que la page n'a pas consommée (bord d'un champ de saisie), pas de la fin de page.
 *
 * Kotlin pur, thread principal. Les instants viennent d'une horloge monotone (`SystemClock.uptimeMillis`) : une
 * fenêtre de quelques centaines de millisecondes ne doit pas dépendre de l'heure murale.
 */
class FocusWrap(private val windowMs: Long = WINDOW_MS) {
    private var armedBackwards: Boolean? = null
    private var armedUntilMs: Long = 0

    /** Un Tab ([isBackwards] = Maj+Tab) vient d'être envoyé par l'utilisateur à [nowMs]. */
    fun armed(isBackwards: Boolean, nowMs: Long) {
        armedBackwards = isBackwards
        armedUntilMs = nowMs + windowMs
    }

    /** La vue reçoit une touche ; [isTab] : Tab ou Maj+Tab (le nôtre). Toute autre touche désarme. */
    fun onKeyDispatched(isTab: Boolean) {
        if (!isTab) armedBackwards = null
    }

    /** Le moteur rend le focus à [nowMs]. Renvoie le sens de la réinjection (`true` = Maj+Tab), `null` = rien à faire. */
    fun onEngineTookFocus(nowMs: Long): Boolean? {
        val direction = armedBackwards ?: return null
        armedBackwards = null
        val isInWindow = nowMs <= armedUntilMs
        return if (isInWindow) direction else null
    }

    companion object {
        const val WINDOW_MS = 400L
    }
}

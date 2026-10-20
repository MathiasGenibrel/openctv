package com.openctv.browser.core.webview

import android.content.Context
import android.view.PointerIcon
import android.webkit.WebView

/** Ce que la page affiche sous le pointeur, d'après l'icône de curseur qu'elle demande (`cursor: pointer`, `text`…). */
enum class PointerKind { Default, Clickable, Text }

/**
 * `WebView` qui remonte le type de curseur demandé par la page. Chromium applique `cursor:` du CSS en
 * appelant `View.setPointerIcon` sur sa vue conteneur, à chaque événement de survol souris : c'est le seul
 * signal « lien / bouton / champ texte » disponible sans JavaScript injecté. Créée uniquement par
 * [LockedWebViewFactory], qui garde la configuration de la WebView.
 *
 * Repli si une version du moteur n'émet pas d'icône : [PointerKind.Default] reste le dernier état connu,
 * le pointeur garde sa forme de repos (le clic fonctionne quand même).
 */
class PointerAwareWebView(context: Context) : WebView(context) {
    // Nullables : `setPointerIcon` peut être appelée par le constructeur parent, avant l'initialisation de ces champs.
    private var handIcon: PointerIcon? = PointerIcon.getSystemIcon(context, PointerIcon.TYPE_HAND)
    private var textIcon: PointerIcon? = PointerIcon.getSystemIcon(context, PointerIcon.TYPE_TEXT)
    private var verticalTextIcon: PointerIcon? = PointerIcon.getSystemIcon(context, PointerIcon.TYPE_VERTICAL_TEXT)

    /** Dernier type demandé par la page. */
    var pointerKind: PointerKind = PointerKind.Default
        private set

    /** Appelé sur le thread UI quand [pointerKind] change. */
    var onPointerKindChanged: ((PointerKind) -> Unit)? = null

    override fun setPointerIcon(pointerIcon: PointerIcon?) {
        super.setPointerIcon(pointerIcon)
        val kind = kindOf(pointerIcon)
        if (kind == pointerKind) return
        pointerKind = kind
        onPointerKindChanged?.invoke(kind)
    }

    private fun kindOf(icon: PointerIcon?): PointerKind = when {
        icon == null -> PointerKind.Default
        icon == handIcon -> PointerKind.Clickable
        icon == textIcon || icon == verticalTextIcon -> PointerKind.Text
        else -> PointerKind.Default
    }
}

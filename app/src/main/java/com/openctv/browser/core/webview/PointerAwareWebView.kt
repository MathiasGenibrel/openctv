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
 * **Détection best-effort, échec silencieux.** L'icône reçue est comparée aux icônes système `HAND` et
 * `TEXT` (`PointerIcon.equals` compare le type des icônes système). Tout ce qui ne correspond pas — moteur
 * qui n'appelle pas `setPointerIcon`, icône personnalisée (`cursor: url(…)`), type inattendu — donne
 * [PointerKind.Default] : le pointeur garde sa forme de repos. Rien d'autre n'en dépend : le clic part
 * toujours au point visé, quel que soit [pointerKind]. Aucune exception possible sur ce chemin.
 */
class PointerAwareWebView(context: Context) : WebView(context) {
    // Lues comme `null` si le constructeur parent appelle déjà `setPointerIcon` (champs pas encore initialisés) :
    // la comparaison échoue alors simplement vers `Default`.
    private val handIcon: PointerIcon? = PointerIcon.getSystemIcon(context, PointerIcon.TYPE_HAND)
    private val textIcon: PointerIcon? = PointerIcon.getSystemIcon(context, PointerIcon.TYPE_TEXT)
    private val verticalTextIcon: PointerIcon? = PointerIcon.getSystemIcon(context, PointerIcon.TYPE_VERTICAL_TEXT)

    /**
     * Échelle à laquelle la page est affichée par rapport à sa mise en page (`1` à 100 %, `0,5` à 50 % : la vue est
     * deux fois plus grande que ce qu'on voit, cf. [applyPageZoom]). Les coordonnées de l'écran sont divisées par
     * cette échelle pour devenir celles de la vue : survol, clic et défilement du curseur.
     */
    var pageScale: Float = 1f
        internal set

    /** Dernier type demandé par la page. */
    var pointerKind: PointerKind = PointerKind.Default
        private set

    /** Appelé sur le thread UI quand [pointerKind] change. */
    var onPointerKindChanged: ((PointerKind) -> Unit)? = null

    /**
     * Envoie [command] à la vidéo plein écran de la page. À n'appeler qu'en plein écran vidéo. Script ponctuel et
     * constant ([MediaCommand]), résultat ignoré, sans état ni interface exposée à la page.
     */
    fun runMediaCommand(command: MediaCommand) {
        evaluateJavascript(command.script, null)
    }

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

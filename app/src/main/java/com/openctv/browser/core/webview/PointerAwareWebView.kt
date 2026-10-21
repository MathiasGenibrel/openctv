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

    /** Dernier type demandé par la page. */
    var pointerKind: PointerKind = PointerKind.Default
        private set

    /** Appelé sur le thread UI quand [pointerKind] change. */
    var onPointerKindChanged: ((PointerKind) -> Unit)? = null

    /**
     * Lecture / pause de la vidéo plein écran de la page (ou, à défaut, de la première vidéo). Les sites vidéo
     * mobiles (YouTube) n'ont aucun raccourci clavier : c'est le seul moyen qu'a OK de les piloter. Appel natif
     * ponctuel, sans état ni interface exposée à la page.
     */
    fun togglePlayback() {
        evaluateJavascript(playerScript("if(v.paused){v.play();}else{v.pause();}"), null)
    }

    /** Recule (< 0) ou avance (> 0) la vidéo de [seconds] secondes, comme [togglePlayback]. */
    fun seekBy(seconds: Int) {
        evaluateJavascript(playerScript("v.currentTime=Math.max(0,v.currentTime+($seconds));"), null)
    }

    private fun playerScript(action: String): String =
        "(function(){var f=document.fullscreenElement;" +
            "var v=(f&&(f.tagName==='VIDEO'?f:f.querySelector('video')))||document.querySelector('video');" +
            "if(!v)return;$action})()"

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

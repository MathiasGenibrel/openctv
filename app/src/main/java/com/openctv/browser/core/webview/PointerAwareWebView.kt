package com.openctv.browser.core.webview

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.view.PointerIcon
import android.view.View
import android.webkit.WebView
import com.openctv.browser.core.zoom.PageZoom
import kotlin.math.roundToInt

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
     * deux fois plus grande que ce qu'on voit, cf. [pageZoomLayout]). Seul [applyPageZoom] la change, en même temps
     * que l'échelle de la vue ; [toViewCoordinate] et [scrollByOnScreen] sont les seules conversions écran → vue.
     */
    private var pageScale: Float = 1f

    /** Dernier type demandé par la page. */
    var pointerKind: PointerKind = PointerKind.Default
        private set

    /** Appelé sur le thread UI quand [pointerKind] change. */
    var onPointerKindChanged: ((PointerKind) -> Unit)? = null

    /**
     * Envoie [command] à la vidéo de la page (plein écran ou non). Script ponctuel et
     * constant ([MediaCommand]), résultat ignoré, sans état ni interface exposée à la page.
     */
    fun runMediaCommand(command: MediaCommand) {
        evaluateJavascript(command.script, null)
    }

    /**
     * Réduit la vue au facteur de [zoom] (pivot en haut à gauche), à placer avec le même [zoom] que
     * [pageZoomLayout]. Les transformations d'une vue sont connues de son parent (rectangle visible, entrées) : la
     * page n'est rastérisée que là où elle est vue. À 100 %, la vue revient à son état sans zoom (échelle 1, pivot
     * par défaut). Ne recharge pas la page.
     */
    fun applyPageZoom(zoom: PageZoom) {
        val factor = zoom.factor
        pageScale = factor
        if (zoom == PageZoom.Full) {
            scaleX = 1f
            scaleY = 1f
            resetPivot()
        } else {
            pivotX = 0f
            pivotY = 0f
            scaleX = factor
            scaleY = factor
        }
    }

    /** Coordonnée [onScreen] (pixels de l'écran, depuis le coin haut gauche de la page) dans la vue agrandie par le zoom. */
    fun toViewCoordinate(onScreen: Float): Float = onScreen / pageScale

    /** Défile de ([dx], [dy]) pixels de l'écran : la vue, agrandie par le zoom de la page, défile d'autant de fois plus. */
    fun scrollByOnScreen(dx: Int, dy: Int) {
        scrollBy((dx / pageScale).roundToInt(), (dy / pageScale).roundToInt())
    }

    /** Défile d'un écran vers [direction] : un écran = la hauteur affichée (la vue est plus haute que l'écran sous zoom). */
    fun scrollOneScreen(direction: ScrollDirection) {
        val visibleHeightOnScreen = (height * pageScale).roundToInt()
        scrollByOnScreen(0, pageScrollPixels(visibleHeightOnScreen, direction))
    }

    /**
     * La page ne rend jamais le focus d'elle-même : seule l'application le déplace (`requestFocus` explicite vers la
     * barre, une surcouche, l'écran d'erreur). Tab après le dernier élément de la page (Maj+Tab avant le premier)
     * fait demander au moteur la vue voisine (`takeFocus` → `focusSearch`) ; une flèche que la page ne consomme pas
     * (début d'un champ de saisie) aussi. Répondre la WebView elle-même garde le focus Android ici : le focus de la
     * page repart du début au Tab suivant, et la barre, non focalisable en mode page, n'est jamais atteinte par surprise.
     */
    override fun focusSearch(direction: Int): View {
        val reinjectBackwards = focusWrap.onEngineTookFocus(SystemClock.uptimeMillis())
        // Tab au-delà du dernier élément : on boucle au premier, après le retour du moteur (pas dans son rappel).
        if (reinjectBackwards != null) post { reinjectElementTab(reinjectBackwards) }
        return this
    }

    private val focusWrap = FocusWrap()

    /**
     * Le mode éléments ne doit pas ouvrir le clavier tout seul quand Tab arrive sur un champ : le moteur ne montre l'IME
     * que si la vue se déclare éditeur de texte. Entre un Tab ([sendElementTab]) et le prochain OK ([allowSoftKeyboard])
     * ou clic, [onCheckIsTextEditor] répond donc `false` au système (pas de connexion d'IME, pas de clavier) ; la page,
     * elle, garde son champ focalisé et [isEditorFocusedInEngine] dit la vérité du moteur. Aucun JavaScript.
     */
    private var isSoftKeyboardHeld = false

    override fun onCheckIsTextEditor(): Boolean = !isSoftKeyboardHeld && super.onCheckIsTextEditor()

    /** Un champ de saisie éditable a le focus dans le moteur, que le clavier soit retenu ou non. */
    val isEditorFocusedInEngine: Boolean
        get() = hasFocus() && super.onCheckIsTextEditor()

    /** OK sur un champ : le clavier est de nouveau permis, et le système est invité à relire l'éditeur. */
    fun allowSoftKeyboard() {
        if (!isSoftKeyboardHeld) return
        isSoftKeyboardHeld = false
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.restartInput(this)
    }

    /** Un clic du curseur (événement tactile injecté) rend au moteur la main sur le clavier : toucher un champ l'ouvre. */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) isSoftKeyboardHeld = false
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        focusWrap.onKeyDispatched(isTab = event.keyCode == KeyEvent.KEYCODE_TAB)
        return super.dispatchKeyEvent(event)
    }

    /**
     * Réinjection du bouclage, au tour suivant de la boucle : rien si la vue a été retirée (écran quitté, WebView
     * détruite) ou si elle n'a plus le focus (barre, surcouche ouvertes entre-temps) : la page ne reçoit une touche
     * que tant qu'elle est l'élément actif.
     */
    private fun reinjectElementTab(isBackwards: Boolean) {
        val isStillThePage = isAttachedToWindow && hasFocus()
        if (isStillThePage) sendElementTab(isBackwards, isUserPress = false)
    }

    /**
     * Tab ou Maj+Tab du mode éléments. Un appui de l'utilisateur ([isUserPress]) arme le bouclage de fin de page
     * ([FocusWrap]) ; la réinjection qui en découle ne l'arme pas.
     */
    fun sendElementTab(isBackwards: Boolean, isUserPress: Boolean = true) {
        if (isUserPress) focusWrap.armed(isBackwards, SystemClock.uptimeMillis())
        isSoftKeyboardHeld = true
        if (isBackwards) sendShiftTab() else sendTab()
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

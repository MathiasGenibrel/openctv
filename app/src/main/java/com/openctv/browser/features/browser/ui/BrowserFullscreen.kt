package com.openctv.browser.features.browser.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.openctv.browser.core.webview.FullscreenHost

/**
 * Où le lecteur plein écran est monté. [show] et [hide] sont appelés sur le thread principal, [show] **dans**
 * `onShowCustomView` (voir [DecorFullscreenPresenter]).
 */
internal interface FullscreenPresenter {
    fun show(video: View)
    fun hide(video: View)

    companion object {
        val None = object : FullscreenPresenter {
            override fun show(video: View) = Unit
            override fun hide(video: View) = Unit
        }
    }
}

/**
 * Vidéo plein écran côté vue : détient la [View] du lecteur et le callback de la WebView le temps du plein
 * écran (une View ne vit pas dans le ViewModel, qui n'en garde que le booléen `isFullscreen`).
 */
internal class BrowserFullscreen(
    private val onShown: () -> Unit,
    private val onHidden: () -> Unit,
    private val presenter: FullscreenPresenter = FullscreenPresenter.None,
) : FullscreenHost {
    var view: View? by mutableStateOf(null)
        private set
    private var callback: WebChromeClient.CustomViewCallback? = null

    override fun onShow(view: View, callback: WebChromeClient.CustomViewCallback) {
        val isAlreadyShowing = this.view != null
        if (isAlreadyShowing) {
            // Un second plein écran sans sortie du premier : on rend le premier à la page.
            callback.onCustomViewHidden()
            return
        }
        // Montage immédiat, pas au prochain passage de composition : voir DecorFullscreenPresenter.
        presenter.show(view)
        this.view = view
        this.callback = callback
        onShown()
    }

    override fun onHide() {
        val shown = view ?: return
        presenter.hide(shown)
        view = null
        callback = null
        onHidden()
    }

    /** Retour : demande à la WebView de quitter ; elle rappelle [onHide] qui libère la vue. */
    fun exit() {
        val pending = callback ?: return
        pending.onCustomViewHidden()
        onHide()
    }
}

/**
 * Monte le lecteur **directement dans le `decorView`** de la fenêtre (montage prévu par Android pour
 * `onShowCustomView`), plein écran dès l'attachement, avec le focus Android pour que OK / flèches atteignent
 * le lecteur sans passer par le focus Compose. Écran maintenu allumé tant qu'il est affiché ; Retour est
 * intercepté par le conteneur et remis à l'`OnBackPressedDispatcher` (donc au `BackHandler` de l'écran, qui
 * quitte le plein écran) : la page ne peut ni l'avaler ni le détourner.
 *
 * **Le montage est synchrone, dans `onShowCustomView`.** Tant que la vue du lecteur n'est pas attachée à la
 * fenêtre, Chromium passe la page en `visibilityState = "hidden"` : YouTube met alors la vidéo en pause (puis
 * recharge le média à `fullscreenchange`) et ne la relance pas, d'où le lecteur figé (« decoder is paused
 * timeout 3s »). Un montage différé par l'état Compose (une recomposition plus tard) suffisait à déclencher
 * ce `hidden` ; attaché dans l'appel même, il n'y a plus de `hidden`.
 * La WebView reste attachée, visible et non mise en pause dessous : la masquer mettrait aussi la page en pause.
 */
internal class DecorFullscreenPresenter(
    private val activity: Activity,
    private val backDispatcher: OnBackPressedDispatcher?,
    private val onPlayerAction: (PlayerAction) -> Unit = {},
) : FullscreenPresenter {
    private var container: FullscreenContainer? = null

    override fun show(video: View) {
        val decor = activity.window.decorView as? FrameLayout ?: return
        val mounted = FullscreenContainer(decor.context, backDispatcher, onPlayerAction)
        (video.parent as? ViewGroup)?.removeView(video)
        mounted.addView(video, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        decor.addView(mounted, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        mounted.hideDefaultFocusHighlight()
        mounted.requestFocus()
        container = mounted
    }

    override fun hide(video: View) {
        val mounted = container ?: return
        container = null
        // Libération : la View du lecteur ne reste attachée à rien une fois le plein écran quitté.
        mounted.removeAllViews()
        (mounted.parent as? ViewGroup)?.removeView(mounted)
    }
}

/**
 * Fond noir plein écran ; prend le focus s'il n'en reste aucun au lecteur, pour que Compose ne le reprenne pas.
 * Retour ne descend pas jusqu'au lecteur : son relâchement est remis à [backDispatcher].
 * OK, flèches : commandes du lecteur ([FullscreenKeys]).
 */
private class FullscreenContainer(
    context: Context,
    private val backDispatcher: OnBackPressedDispatcher?,
    private val onPlayerAction: (PlayerAction) -> Unit,
) : FrameLayout(context) {
    init {
        setBackgroundColor(Color.BLACK)
        keepScreenOn = true
        isFocusable = true
        isFocusableInTouchMode = true
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
    }

    /**
     * Pas de contour de focus Android par défaut (le petit carré bleu en haut à gauche) : le lecteur dessine
     * lui-même ses états. Appliqué à toute la sous-arbre montée.
     */
    fun hideDefaultFocusHighlight() = hideDefaultFocusHighlight(this)

    private fun hideDefaultFocusHighlight(view: View) {
        view.defaultFocusHighlightEnabled = false
        if (view is ViewGroup) for (i in 0 until view.childCount) hideDefaultFocusHighlight(view.getChildAt(i))
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Les vues du lecteur apparaissent après le montage : le contour est retiré à chaque touche aussi.
        hideDefaultFocusHighlight(this)
        val isBack = event.keyCode == KeyEvent.KEYCODE_BACK
        val dispatcher = backDispatcher
        if (isBack && dispatcher != null) {
            val isRelease = event.action == KeyEvent.ACTION_UP && !event.isCanceled
            if (isRelease) dispatcher.onBackPressed()
            return true
        }
        if (FullscreenKeys.isPlayerKey(event.keyCode)) {
            onPlayerAction(FullscreenKeys.actionFor(event.keyCode, event.action, event.repeatCount))
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

private const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT

/** Barres système masquées tant que le plein écran vidéo est affiché. */
@Composable
internal fun ImmersiveMode() {
    val window = LocalContext.current.findActivity()?.window ?: return
    DisposableEffect(window) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Ce que fait une touche en plein écran vidéo. */
internal enum class PlayerAction { TogglePlayback, SeekBack, SeekForward, None }

/**
 * Touches du lecteur plein écran. YouTube web mobile n'a aucun raccourci clavier (Entrée, Espace, Lecture/Pause
 * et flèches ne font rien d'utile) et, si la page reçoit une flèche, Chromium dessine un contour de focus bleu
 * autour du lecteur : toutes les touches sont donc **consommées** ici et traduites en commandes de lecteur
 * ([PointerAwareWebView.togglePlayback] / [PointerAwareWebView.seekBy]), uniquement en plein écran.
 * Un appui maintenu ne répète pas OK (pas de clignotement de la lecture) mais répète le saut.
 */
internal object FullscreenKeys {
    const val SEEK_SECONDS = 10

    fun actionFor(keyCode: Int, action: Int, repeatCount: Int): PlayerAction {
        val isDown = action == KeyEvent.ACTION_DOWN
        val isFirstDown = isDown && repeatCount == 0
        return when {
            keyCode in confirmKeys && isFirstDown -> PlayerAction.TogglePlayback
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT && isDown -> PlayerAction.SeekBack
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && isDown -> PlayerAction.SeekForward
            else -> PlayerAction.None
        }
    }

    /** Touches que le lecteur prend pour lui : la page ne les reçoit jamais. */
    fun isPlayerKey(keyCode: Int): Boolean = keyCode in confirmKeys || keyCode in arrowKeys

    private val confirmKeys = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)
    private val arrowKeys = setOf(
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
    )
}

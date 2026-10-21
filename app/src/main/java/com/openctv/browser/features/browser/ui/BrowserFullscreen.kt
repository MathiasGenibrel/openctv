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
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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
 * Vidéo plein écran côté vue : détient la [View] du lecteur et le callback de la WebView le temps du plein
 * écran (une View ne vit pas dans le ViewModel, qui n'en garde que le booléen `isFullscreen`).
 */
internal class BrowserFullscreen(
    private val onShown: () -> Unit,
    private val onHidden: () -> Unit,
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
        this.view = view
        this.callback = callback
        onShown()
    }

    override fun onHide() {
        val isShowing = view != null
        if (!isShowing) return
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
 * Le lecteur au-dessus de tout, barres système masquées et écran maintenu allumé tant qu'il est affiché.
 *
 * La vue du lecteur est attachée **directement au `decorView` de la fenêtre**, pas à la hiérarchie Compose
 * (`AndroidView`) : c'est le montage prévu par Android pour `onShowCustomView`. Causes retenues du lecteur
 * figé (YouTube, « decoder is paused timeout 3s », non reproductible hors TV) : la vue hébergée par
 * Compose n'est dimensionnée qu'au passage de layout Compose (surface vidéo d'abord à taille nulle), et les
 * touches OK / flèches transitaient par le focus Compose (resté sur la page) au lieu d'atteindre le lecteur.
 * Attachée au decor, la vue est plein écran dès l'attachement et, avec le focus Android, reçoit les touches
 * de la télécommande directement.
 * Seul endroit qui donne le focus au lecteur (à l'attachement) ; Retour est intercepté par le conteneur et
 * remis à l'`OnBackPressedDispatcher` (donc au `BackHandler` de l'écran, qui quitte le plein écran) : la page
 * ne peut ni l'avaler ni le détourner.
 * La WebView reste attachée, visible et non mise en pause dessous : la masquer mettrait la page (donc la
 * vidéo) en pause côté Chromium.
 */
@Composable
internal fun FullscreenLayer(video: View) {
    ImmersiveMode()
    val decor = LocalContext.current.findActivity()?.window?.decorView as? FrameLayout ?: return
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    DisposableEffect(decor, video, backDispatcher) {
        val container = FullscreenContainer(decor.context, backDispatcher)
        (video.parent as? ViewGroup)?.removeView(video)
        container.addView(video, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        decor.addView(container, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        container.requestFocus()
        onDispose {
            // Libération : la View du lecteur ne reste attachée à rien une fois le plein écran quitté.
            container.removeAllViews()
            decor.removeView(container)
        }
    }
}

/**
 * Fond noir plein écran ; prend le focus s'il n'en reste aucun au lecteur, pour que Compose ne le reprenne pas.
 * Retour ne descend pas jusqu'au lecteur : son relâchement est remis à [backDispatcher].
 */
private class FullscreenContainer(
    context: Context,
    private val backDispatcher: OnBackPressedDispatcher?,
) : FrameLayout(context) {
    init {
        setBackgroundColor(Color.BLACK)
        keepScreenOn = true
        isFocusable = true
        isFocusableInTouchMode = true
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isBack = event.keyCode == KeyEvent.KEYCODE_BACK
        val dispatcher = backDispatcher
        if (!isBack || dispatcher == null) return super.dispatchKeyEvent(event)
        val isRelease = event.action == KeyEvent.ACTION_UP && !event.isCanceled
        if (isRelease) dispatcher.onBackPressed()
        return true
    }
}

private const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT

@Composable
private fun ImmersiveMode() {
    val window = LocalContext.current.findActivity()?.window ?: return
    DisposableEffect(window) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

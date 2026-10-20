package com.openctv.browser.features.browser.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
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

/** Le lecteur au-dessus de tout, barres système masquées et écran maintenu allumé tant qu'il est affiché. */
@Composable
internal fun FullscreenLayer(video: View, modifier: Modifier = Modifier) {
    ImmersiveMode()
    AndroidView(
        factory = { context ->
            FrameLayout(context).apply {
                keepScreenOn = true
                (video.parent as? ViewGroup)?.removeView(video)
                addView(video, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            }
        },
        modifier = modifier.fillMaxSize(),
        // Libération : la View du lecteur ne reste attachée à rien une fois le plein écran quitté.
        onRelease = { container -> container.removeAllViews() },
    )
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

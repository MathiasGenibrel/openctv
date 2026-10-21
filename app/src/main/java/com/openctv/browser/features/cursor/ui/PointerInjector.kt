package com.openctv.browser.features.cursor.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import com.openctv.browser.core.webview.PointerAwareWebView
import com.openctv.browser.features.cursor.domain.PointerAction
import com.openctv.browser.features.cursor.domain.PointerEvent

/**
 * Injecte le clic et le survol du curseur dans la [PointerAwareWebView]. Les coordonnées reçues sont celles de
 * l'écran (zone de la page) : elles sont ramenées à celles de la vue quand la page est zoomée ([PointerAwareWebView.pageScale]).
 *
 * - **Clic** (`Down` / `Up`) : événements **tactiles** (`SOURCE_TOUCHSCREEN`, doigt). Chromium en tire un vrai
 *   tap : lien, bouton, focus d'un champ texte **et ouverture du clavier TV** (un clic « souris » ne rouvre pas
 *   toujours l'IME). Le `Up` part au point exact du `Down` ; un appui long déclenche l'appui long de la page.
 * - **Survol** ([hover]) : événements **souris** (`SOURCE_MOUSE`, `ACTION_HOVER_MOVE`) : la page applique
 *   son `:hover` et demande l'icône de pointeur (`cursor:`) qui fait changer la forme du curseur.
 *
 * Aucun JavaScript injecté, aucune interface exposée à la page. Les objets de propriétés sont réutilisés et
 * le `MotionEvent` vient du pool d'Android (recyclé aussitôt) : rien n'est alloué par frame de survol.
 */
internal class PointerInjector(private val webView: PointerAwareWebView) {
    private val properties = arrayOf(MotionEvent.PointerProperties())
    private val coords = arrayOf(MotionEvent.PointerCoords())
    private var downTime = 0L

    /** Clic : `DOWN` puis `UP`, au point donné par le contrôleur. */
    fun inject(event: PointerEvent) {
        val now = SystemClock.uptimeMillis()
        val action = when (event.action) {
            PointerAction.Down -> MotionEvent.ACTION_DOWN.also { downTime = now }
            PointerAction.Up -> MotionEvent.ACTION_UP
        }
        val scale = webView.pageScale
        val motion = obtain(downTime, now, action, event.x / scale, event.y / scale, MotionEvent.TOOL_TYPE_FINGER, InputDevice.SOURCE_TOUCHSCREEN)
        webView.dispatchTouchEvent(motion)
        motion.recycle()
    }

    /** Survol souris en (x, y), coordonnées de la zone de la page à l'écran. */
    fun hover(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val scale = webView.pageScale
        val motion = obtain(now, now, MotionEvent.ACTION_HOVER_MOVE, x / scale, y / scale, MotionEvent.TOOL_TYPE_MOUSE, InputDevice.SOURCE_MOUSE)
        webView.dispatchGenericMotionEvent(motion)
        motion.recycle()
    }

    private fun obtain(downTime: Long, eventTime: Long, action: Int, x: Float, y: Float, toolType: Int, source: Int): MotionEvent {
        properties[0].clear()
        properties[0].id = 0
        properties[0].toolType = toolType
        coords[0].clear()
        coords[0].x = x
        coords[0].y = y
        coords[0].pressure = 1f
        coords[0].size = 1f
        return MotionEvent.obtain(
            downTime, eventTime, action, 1, properties, coords, 0, 0, 1f, 1f, 0, 0, source, 0,
        )
    }
}

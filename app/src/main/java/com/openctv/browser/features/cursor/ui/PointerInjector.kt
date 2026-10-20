package com.openctv.browser.features.cursor.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.webkit.WebView
import com.openctv.browser.features.cursor.domain.PointerAction
import com.openctv.browser.features.cursor.domain.PointerEvent

/**
 * Injecte les [PointerEvent] du curseur dans la [WebView].
 *
 * - **Clic** (`Down` / `Up`) : événements **tactiles** (`SOURCE_TOUCHSCREEN`, doigt). Chromium en tire un vrai
 *   tap : lien, bouton, focus d'un champ texte **et ouverture du clavier TV** (un clic « souris » ne rouvre pas
 *   toujours l'IME). Le `Up` part au point exact du `Down` ; un appui long déclenche l'appui long de la page.
 * - **Survol** (`HoverMove`) : événements **souris** (`SOURCE_MOUSE`, `ACTION_HOVER_MOVE`) : la page applique
 *   son `:hover` et demande l'icône de pointeur (`cursor:`) qui fait changer la forme du curseur.
 *
 * Aucun JavaScript injecté, aucune interface exposée à la page. Les objets de propriétés sont réutilisés :
 * seul le `MotionEvent` lui-même est alloué (et recyclé).
 */
internal class PointerInjector(private val webView: WebView) {
    private val properties = arrayOf(MotionEvent.PointerProperties())
    private val coords = arrayOf(MotionEvent.PointerCoords())
    private var downTime = 0L

    fun inject(event: PointerEvent) {
        val now = SystemClock.uptimeMillis()
        when (event.action) {
            PointerAction.Down -> {
                downTime = now
                dispatchTouch(MotionEvent.ACTION_DOWN, now, event)
            }
            PointerAction.Up -> dispatchTouch(MotionEvent.ACTION_UP, now, event)
            PointerAction.HoverMove -> dispatchHover(now, event)
        }
    }

    private fun dispatchTouch(action: Int, now: Long, event: PointerEvent) {
        val motion = obtain(downTime, now, action, event, MotionEvent.TOOL_TYPE_FINGER, InputDevice.SOURCE_TOUCHSCREEN)
        webView.dispatchTouchEvent(motion)
        motion.recycle()
    }

    private fun dispatchHover(now: Long, event: PointerEvent) {
        val motion = obtain(now, now, MotionEvent.ACTION_HOVER_MOVE, event, MotionEvent.TOOL_TYPE_MOUSE, InputDevice.SOURCE_MOUSE)
        webView.dispatchGenericMotionEvent(motion)
        motion.recycle()
    }

    private fun obtain(downTime: Long, eventTime: Long, action: Int, event: PointerEvent, toolType: Int, source: Int): MotionEvent {
        properties[0].clear()
        properties[0].id = 0
        properties[0].toolType = toolType
        coords[0].clear()
        coords[0].x = event.x
        coords[0].y = event.y
        coords[0].pressure = 1f
        coords[0].size = 1f
        return MotionEvent.obtain(
            downTime, eventTime, action, 1, properties, coords, 0, 0, 1f, 1f, 0, 0, source, 0,
        )
    }
}

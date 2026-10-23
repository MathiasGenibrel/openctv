package com.openctv.browser.core.webview

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView

/**
 * Envoie à la WebView un appui complet (down puis up) de Tab, Maj+Tab ou Entrée : le moteur déplace le focus de la
 * page, dessine son contour (`:focus-visible`) et fait défiler l'élément. **Aucun JavaScript** : uniquement des
 * `KeyEvent`. Thread principal.
 */
fun WebView.sendKeyPress(keyCode: Int, metaState: Int = 0) {
    val downTime = SystemClock.uptimeMillis()
    dispatchKeyEvent(KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
    dispatchKeyEvent(KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0, metaState))
}

/** Tab : élément suivant. */
fun WebView.sendTab() = sendKeyPress(KeyEvent.KEYCODE_TAB)

/** Maj+Tab : élément précédent. */
fun WebView.sendShiftTab() = sendKeyPress(KeyEvent.KEYCODE_TAB, KeyEvent.META_SHIFT_ON)

/** Entrée : active l'élément focalisé (lien, bouton). */
fun WebView.sendEnter() = sendKeyPress(KeyEvent.KEYCODE_ENTER)

/** Ouvre le clavier système sur le champ de saisie focalisé de la page. */
fun WebView.showSoftKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.showSoftInput(this, 0)
}

/** Tab / Maj+Tab du mode éléments, bouclant en fin de page ([FocusWrap]) quand la vue le permet ; sinon appui simple. */
fun WebView.sendTabWrapping(isBackwards: Boolean) {
    val aware = this as? PointerAwareWebView
    when {
        aware != null -> aware.sendElementTab(isBackwards)
        isBackwards -> sendShiftTab()
        else -> sendTab()
    }
}

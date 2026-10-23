package com.openctv.browser.core.webview

import android.webkit.WebView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Un champ de saisie de la page a le focus (clavier système ouvert ou non) : la page attend du texte. Lu dans l'état
 * d'IME du moteur (`onCheckIsTextEditor` : vrai seulement pour un champ éditable focalisé), sans JavaScript ; `false`
 * si la WebView elle-même n'a pas le focus (la touche n'irait pas à la page). Brut : `PageTextFocus` décide s'il est
 * fiable. Thread principal.
 */
val WebView.isTextFieldFocused: Boolean
    get() = hasFocus() && onCheckIsTextEditor()

/**
 * Le clavier système est affiché (inset IME visible dans la fenêtre). Brut aussi : `PageTextFocus` ne le croit que sur
 * un champ de saisie fiable. Sans information d'insets (vue détachée), `false`. Thread principal.
 */
val WebView.isKeyboardShown: Boolean
    get() = ViewCompat.getRootWindowInsets(this)?.isVisible(WindowInsetsCompat.Type.ime()) == true

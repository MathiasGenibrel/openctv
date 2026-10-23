package com.openctv.browser.core.webview

import android.webkit.WebView

/**
 * Un champ de saisie de la page a le focus (clavier système ouvert ou non) : la page attend du texte. Lu dans l'état
 * d'IME du moteur (`onCheckIsTextEditor` : vrai seulement pour un champ éditable focalisé), sans JavaScript ; `false`
 * si la WebView elle-même n'a pas le focus (la touche n'irait pas à la page). Brut : `PageTextFocus` décide s'il est
 * fiable. Thread principal.
 */
val WebView.isTextFieldFocused: Boolean
    get() = hasFocus() && onCheckIsTextEditor()

package com.openctv.browser.core.designsystem

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties

/**
 * Groupe de focus modal (surcouche plein écran) : le D-pad ne peut pas en sortir vers l'écran resté
 * vivant dessous. Seule la fermeture de la surcouche rend le focus à cet écran.
 */
fun Modifier.focusTrap(): Modifier = focusProperties { onExit = { cancelFocusChange() } }.focusGroup()

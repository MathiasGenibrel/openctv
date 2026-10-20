package com.openctv.browser.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Marge de sécurité d'overscan (~5 % de 1920x1080). */
private val OverscanHorizontal = 48.dp
private val OverscanVertical = 27.dp

/**
 * Zone sûre des écrans d'interface : rien d'important n'est collé au bord de la TV.
 * Séparée du thème pour que le plein écran vidéo et la page web puissent occuper tout l'écran.
 */
@Composable
fun OverscanSafeArea(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = OverscanHorizontal, vertical = OverscanVertical),
    ) {
        content()
    }
}

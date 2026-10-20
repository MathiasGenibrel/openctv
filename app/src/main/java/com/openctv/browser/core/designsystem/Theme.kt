package com.openctv.browser.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Marge de sécurité d'overscan (~5 % de 1920x1080). */
private val OverscanHorizontal = 48.dp
private val OverscanVertical = 27.dp

@Composable
fun OpenCtvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = OverscanHorizontal, vertical = OverscanVertical),
        ) {
            content()
        }
    }
}

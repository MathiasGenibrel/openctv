package com.openctv.browser.core.designsystem

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Thème TV d'OpenCTV : couleurs et typographie uniquement, sans imposer de mise en page. */
@Composable
fun OpenCtvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}

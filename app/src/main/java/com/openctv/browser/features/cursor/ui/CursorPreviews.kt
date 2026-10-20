package com.openctv.browser.features.cursor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.openctv.browser.core.designsystem.OpenCtvTheme

private fun restingVisuals(configure: CursorVisuals.() -> Unit = {}) = CursorVisuals().apply {
    x.snapTo(60f)
    y.snapTo(60f)
    alpha.snapTo(1f)
    configure()
}

@Composable
private fun CursorSample(background: Color, visuals: CursorVisuals) {
    val painter = remember { CursorPainter() }
    Canvas(Modifier.size(120.dp).background(background)) {
        with(painter) { drawCursor(visuals, originX = 0f, originY = 0f) }
    }
}

/** Repos, survol cliquable, survol texte et appui, sur fonds clair et sombre (visible sur tout fond). */
@Preview(device = Devices.TV_1080p)
@Composable
private fun CursorStatesPreview() {
    OpenCtvTheme {
        Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CursorSample(Color.White, restingVisuals())
            CursorSample(Color.Black, restingVisuals())
            CursorSample(Color.Black, restingVisuals { clickable.snapTo(1f) })
            CursorSample(Color.White, restingVisuals { text.snapTo(1f) })
            CursorSample(Color.White, restingVisuals { press.snapTo(1f) })
        }
    }
}

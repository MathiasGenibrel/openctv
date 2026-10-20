package com.openctv.browser.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** Indication de touche de télécommande : pastille de touche + libellé (« OK », « Retour »…). */
@Composable
fun KeyHint(
    label: String,
    modifier: Modifier = Modifier,
    key: @Composable () -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        key()
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Pastille textuelle d'une touche (« OK », « ⟵ »). */
@Composable
fun KeyCap(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .background(OpenCtvColors.SurfaceContainerHighest, OpenCtvShapes.Small)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Croix directionnelle : disque et quatre chevrons. */
@Composable
fun DPadIcon(modifier: Modifier = Modifier, tint: Color = OpenCtvColors.OnSurfaceVariant) {
    Canvas(modifier = modifier.size(24.dp)) {
        val c = center
        val r = size.minDimension / 2
        drawCircle(tint.copy(alpha = 0.3f), radius = r)
        val d = r * 0.55f
        val a = r * 0.22f
        listOf(Offset(0f, -1f), Offset(0f, 1f), Offset(-1f, 0f), Offset(1f, 0f)).forEach { dir ->
            val perp = Offset(-dir.y, dir.x)
            val tip = c + dir * d
            val path = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo((tip - dir * a + perp * a).x, (tip - dir * a + perp * a).y)
                lineTo((tip - dir * a - perp * a).x, (tip - dir * a - perp * a).y)
                close()
            }
            drawPath(path, tint)
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun KeyHintPreview() {
    OpenCtvTheme {
        Row(modifier = Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            KeyHint("Naviguer") { DPadIcon() }
            KeyHint("Valider") { KeyCap("OK") }
            KeyHint("Retour") { KeyCap("⟵") }
        }
    }
}

package com.openctv.browser.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
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
        // Icônes d'aide : couleur de contenu discrète par défaut (sinon noir hors d'une Surface).
        CompositionLocalProvider(LocalContentColor provides OpenCtvColors.OnSurfaceVariant, content = key)
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

/** Croix directionnelle de la télécommande (symbole Material « gamepad »). */
@Composable
fun DPadIcon(modifier: Modifier = Modifier, tint: Color = OpenCtvColors.OnSurfaceVariant) {
    Icon(OpenCtvIcons.Gamepad, contentDescription = null, modifier = modifier.size(24.dp), tint = tint)
}

@Preview(device = Devices.TV_1080p)
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

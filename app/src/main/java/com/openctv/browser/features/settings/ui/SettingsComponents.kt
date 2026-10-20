package com.openctv.browser.features.settings.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.openctv.browser.core.designsystem.OpenCtvColors
import com.openctv.browser.core.designsystem.OpenCtvShapes
import com.openctv.browser.core.designsystem.OpenCtvTheme
import com.openctv.browser.core.designsystem.focusRingBorder

/** Groupe de lignes de réglages : fond `surfaceContainer`, coins Large. */
@Composable
internal fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(OpenCtvColors.SurfaceContainer, OpenCtvShapes.Large)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

/** Ligne focalisable : libellé + valeur courante, et [trailing] (chevron par défaut). */
@Composable
internal fun SettingsRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = { Chevron() },
) {
    val shape = OpenCtvShapes.Medium
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = shape, focusedShape = shape),
        border = ClickableSurfaceDefaults.border(focusedBorder = focusRingBorder(shape)),
        colors = rowColors(),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = OpenCtvColors.OnSurfaceVariant)
            }
            trailing()
        }
    }
}

/** Ligne à interrupteur : OK bascule la valeur. */
@Composable
internal fun SettingsSwitchRow(
    label: String,
    value: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsRow(
        label = label,
        value = value,
        onClick = { onCheckedChange(!isChecked) },
        modifier = modifier,
        trailing = { Switch(checked = isChecked, onCheckedChange = null) },
    )
}

/** Option exclusive d'un sous-panneau (radio) : [supporting] précise « Par défaut », « Sans pistage »… */
@Composable
internal fun RadioOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    val shape = OpenCtvShapes.Medium
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = shape, focusedShape = shape),
        border = ClickableSurfaceDefaults.border(focusedBorder = focusRingBorder(shape)),
        colors = rowColors(),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RadioMark(isSelected)
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                if (supporting != null) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = OpenCtvColors.OnSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun rowColors() = ClickableSurfaceDefaults.colors(
    containerColor = Color.Transparent,
    contentColor = OpenCtvColors.OnSurface,
    focusedContainerColor = OpenCtvColors.PrimaryContainer,
    focusedContentColor = OpenCtvColors.OnPrimaryContainer,
    pressedContainerColor = OpenCtvColors.PrimaryContainer,
    pressedContentColor = OpenCtvColors.OnPrimaryContainer,
)

@Composable
private fun Chevron() {
    Text("›", style = MaterialTheme.typography.headlineSmall, color = OpenCtvColors.OnSurfaceVariant)
}

@Composable
private fun RadioMark(isSelected: Boolean, modifier: Modifier = Modifier) {
    val color = if (isSelected) OpenCtvColors.Primary else OpenCtvColors.Outline
    Canvas(modifier.size(24.dp)) {
        drawCircle(color, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
        if (isSelected) drawCircle(color, radius = 6.dp.toPx(), center = Offset(size.width / 2, size.height / 2))
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun SettingsComponentsPreview() {
    OpenCtvTheme {
        Column(modifier = Modifier.padding(32.dp).size(width = 560.dp, height = 480.dp)) {
            SettingsGroup {
                SettingsRow("Page de démarrage", "Écran d'accueil OpenCTV", onClick = {})
                SettingsSwitchRow("Bloqueur de pubs", "Activé", isChecked = true, onCheckedChange = {})
                RadioOptionRow("DuckDuckGo", isSelected = true, onClick = {}, supporting = "Par défaut · Sans pistage")
            }
        }
    }
}

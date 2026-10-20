package com.openctv.browser.features.settings.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.openctv.browser.core.designsystem.OpenCtvColors
import com.openctv.browser.core.designsystem.OpenCtvIcons
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

/** Ligne focalisable : [icon], libellé + valeur courante, et [trailing] (chevron par défaut). */
@Composable
internal fun SettingsRow(
    icon: Painter,
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
            Icon(icon, contentDescription = null)
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
    icon: Painter,
    label: String,
    value: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsRow(
        icon = icon,
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
    Icon(OpenCtvIcons.ChevronRight, contentDescription = null, tint = OpenCtvColors.OnSurfaceVariant)
}

@Composable
private fun RadioMark(isSelected: Boolean) {
    val icon = if (isSelected) OpenCtvIcons.RadioButtonChecked else OpenCtvIcons.RadioButtonUnchecked
    val tint = if (isSelected) OpenCtvColors.Primary else OpenCtvColors.Outline
    Icon(icon, contentDescription = null, tint = tint)
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun SettingsComponentsPreview() {
    OpenCtvTheme {
        Column(modifier = Modifier.padding(32.dp).size(width = 560.dp, height = 480.dp)) {
            SettingsGroup {
                SettingsRow(OpenCtvIcons.Home, "Page de démarrage", "Écran d'accueil OpenCTV", onClick = {})
                SettingsSwitchRow(OpenCtvIcons.Shield, "Bloqueur de pubs", "Activé", isChecked = true, onCheckedChange = {})
                RadioOptionRow("DuckDuckGo", isSelected = true, onClick = {}, supporting = "Par défaut · Sans pistage")
            }
        }
    }
}

package com.openctv.browser.features.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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

/** Groupe d'actions « Vibrant » du menu : fond `tertiaryContainer`, coins Large. */
@Composable
internal fun MenuGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(OpenCtvColors.TertiaryContainer, OpenCtvShapes.Large)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

/**
 * Ligne focalisable du menu : [icon], [label], [supporting] optionnel (deuxième ligne) et [trailing]
 * (valeur, interrupteur, chevron). La ligne grandit avec son contenu : rien n'est découpé.
 */
@Composable
internal fun MenuRow(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    val shape = OpenCtvShapes.Medium
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = shape, focusedShape = shape),
        border = ClickableSurfaceDefaults.border(focusedBorder = focusRingBorder(shape)),
        colors = menuRowColors(),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                if (supporting != null) {
                    Text(supporting, style = MaterialTheme.typography.bodyMedium)
                }
            }
            trailing()
        }
    }
}

@Composable
internal fun MenuSwitchRow(
    icon: Painter,
    label: String,
    isChecked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    MenuRow(
        icon = icon,
        label = label,
        supporting = supporting,
        onClick = onClick,
        modifier = modifier,
        trailing = { Switch(checked = isChecked, onCheckedChange = null) },
    )
}

/** Ligne qui ouvre un sous-panneau : [value] courante et chevron. */
@Composable
internal fun MenuNavigationRow(
    icon: Painter,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MenuRow(
        icon = icon,
        label = label,
        supporting = value,
        onClick = onClick,
        modifier = modifier,
        trailing = { Icon(OpenCtvIcons.ChevronRight, contentDescription = null) },
    )
}

/** Option exclusive d'un sous-panneau (radio) ; [supporting] précise « Par défaut », « Sans pistage »… */
@Composable
internal fun MenuRadioRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    val icon = if (isSelected) OpenCtvIcons.RadioButtonChecked else OpenCtvIcons.RadioButtonUnchecked
    MenuRow(icon = icon, label = label, supporting = supporting, onClick = onClick, modifier = modifier)
}

@Composable
private fun menuRowColors() = ClickableSurfaceDefaults.colors(
    containerColor = Color.Transparent,
    contentColor = OpenCtvColors.OnTertiaryContainer,
    focusedContainerColor = OpenCtvColors.Tertiary,
    focusedContentColor = OpenCtvColors.OnTertiary,
    pressedContainerColor = OpenCtvColors.Tertiary,
    pressedContentColor = OpenCtvColors.OnTertiary,
)

@Preview(device = Devices.TV_1080p)
@Composable
private fun MenuComponentsPreview() {
    OpenCtvTheme {
        Column(modifier = Modifier.padding(32.dp).fillMaxWidth(0.3f)) {
            MenuGroup {
                MenuSwitchRow(OpenCtvIcons.Shield, "Bloqueur de pubs", isChecked = true, onClick = {}, supporting = "12 requêtes bloquées sur ce site")
                MenuRow(OpenCtvIcons.Refresh, "Actualiser la page", onClick = {})
                MenuNavigationRow(OpenCtvIcons.Speed, "Vitesse du curseur", "Normale", onClick = {})
                MenuRadioRow("DuckDuckGo", isSelected = true, onClick = {}, supporting = "Par défaut · Sans pistage")
            }
        }
    }
}

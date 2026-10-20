package com.openctv.browser.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.Text

// États désactivés M3 : conteneur 12 %, contenu 38 %.
private const val DisabledContainerAlpha = 0.12f
private const val DisabledContentAlpha = 0.38f

enum class OpenCtvButtonStyle { Filled, Tonal }

private val OpenCtvButtonStyle.container: Color
    get() = if (this == OpenCtvButtonStyle.Filled) OpenCtvColors.Primary else OpenCtvColors.SecondaryContainer

private val OpenCtvButtonStyle.onContainer: Color
    get() = if (this == OpenCtvButtonStyle.Filled) OpenCtvColors.OnPrimary else OpenCtvColors.OnSecondaryContainer

/** Bouton pilule d'OpenCTV : `Filled` (action principale, `primary`) ou `Tonal` (`secondaryContainer`), avec anneau de focus. */
@Composable
fun OpenCtvButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: OpenCtvButtonStyle = OpenCtvButtonStyle.Filled,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = ButtonDefaults.shape(CircleShape),
        border = ButtonDefaults.border(focusedBorder = focusRingBorder(CircleShape)),
        colors = ButtonDefaults.colors(
            containerColor = style.container,
            contentColor = style.onContainer,
            focusedContainerColor = style.container,
            focusedContentColor = style.onContainer,
        ),
        content = content,
    )
}

/**
 * Bouton icône rond d'OpenCTV (40 dp), mêmes styles et anneau de focus que [OpenCtvButton]. `Tonal` par
 * défaut : icônes d'en-tête (Réglages…). Désactivé ([enabled] faux) : grisé et non focalisable. [contentDescription] est obligatoire : pas de libellé visible.
 */
@Composable
fun OpenCtvIconButton(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: OpenCtvButtonStyle = OpenCtvButtonStyle.Tonal,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = IconButtonDefaults.shape(CircleShape),
        border = IconButtonDefaults.border(focusedBorder = focusRingBorder(CircleShape)),
        colors = IconButtonDefaults.colors(
            containerColor = style.container,
            contentColor = style.onContainer,
            focusedContainerColor = style.container,
            focusedContentColor = style.onContainer,
            disabledContainerColor = style.container.copy(alpha = DisabledContainerAlpha),
            disabledContentColor = style.onContainer.copy(alpha = DisabledContentAlpha),
        ),
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun OpenCtvButtonPreview() {
    OpenCtvTheme {
        Row(modifier = Modifier.padding(32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OpenCtvButton(onClick = {}) { Text("Réessayer") }
            OpenCtvButton(onClick = {}, style = OpenCtvButtonStyle.Tonal) { Text("Accueil") }
            OpenCtvIconButton(OpenCtvIcons.Settings, contentDescription = "Réglages", onClick = {})
        }
    }
}

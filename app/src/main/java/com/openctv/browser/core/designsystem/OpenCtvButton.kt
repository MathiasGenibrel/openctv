package com.openctv.browser.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text

enum class OpenCtvButtonStyle { Filled, Tonal }

/** Bouton pilule d'OpenCTV : `Filled` (action principale, `primary`) ou `Tonal` (`secondaryContainer`), avec anneau de focus. */
@Composable
fun OpenCtvButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: OpenCtvButtonStyle = OpenCtvButtonStyle.Filled,
    content: @Composable RowScope.() -> Unit,
) {
    val container = if (style == OpenCtvButtonStyle.Filled) OpenCtvColors.Primary else OpenCtvColors.SecondaryContainer
    val onContainer = if (style == OpenCtvButtonStyle.Filled) OpenCtvColors.OnPrimary else OpenCtvColors.OnSecondaryContainer
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = ButtonDefaults.shape(CircleShape),
        border = ButtonDefaults.border(focusedBorder = focusRingBorder(CircleShape)),
        colors = ButtonDefaults.colors(
            containerColor = container,
            contentColor = onContainer,
            focusedContainerColor = container,
            focusedContentColor = onContainer,
        ),
        content = content,
    )
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun OpenCtvButtonPreview() {
    OpenCtvTheme {
        Row(modifier = Modifier.padding(32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OpenCtvButton(onClick = {}) { Text("Réessayer") }
            OpenCtvButton(onClick = {}, style = OpenCtvButtonStyle.Tonal) { Text("Accueil") }
        }
    }
}

package com.openctv.browser.core.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * Tuile focalisable (favori, entrée de liste) : fond `surfaceContainer`, `primaryContainer` au focus,
 * anneau de focus extérieur qui suit l'agrandissement de la tuile. Le contenu reçoit la couleur de texte
 * adaptée à l'état. [interactionSource] permet à l'appelant d'observer le focus (animations internes).
 */
@Composable
fun FocusableTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val shape = OpenCtvShapes.ExtraLarge
    Surface(
        onClick = onClick,
        modifier = modifier,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape, focusedShape = shape),
        border = ClickableSurfaceDefaults.border(focusedBorder = focusRingBorder(shape)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = OpenCtvColors.SurfaceContainer,
            contentColor = OpenCtvColors.OnSurface,
            focusedContainerColor = OpenCtvColors.PrimaryContainer,
            focusedContentColor = OpenCtvColors.OnPrimaryContainer,
            pressedContainerColor = OpenCtvColors.PrimaryContainer,
            pressedContentColor = OpenCtvColors.OnPrimaryContainer,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        content()
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun FocusableTilePreview() {
    OpenCtvTheme {
        Row(modifier = Modifier.padding(32.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            listOf("Wikipedia" to "wikipedia.org", "Arte" to "arte.tv").forEach { (name, domain) ->
                FocusableTile(onClick = {}, modifier = Modifier.width(204.dp).height(96.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.Center) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        Text(domain, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

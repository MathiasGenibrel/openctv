package com.openctv.browser.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** Snackbar M3 (`inverseSurface`), non focalisable : elle informe (aide, résultat d'une action) sans prendre le focus. */
@Composable
fun OpenCtvSnackbar(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = OpenCtvColors.InverseOnSurface,
        modifier = modifier
            .shadow(elevation = 6.dp, shape = OpenCtvShapes.ExtraSmall)
            .background(OpenCtvColors.InverseSurface, OpenCtvShapes.ExtraSmall)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    )
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun OpenCtvSnackbarPreview() {
    OpenCtvTheme {
        OpenCtvSnackbar("Mode interface : OK pour ouvrir · Bas ou Retour pour revenir à la page")
    }
}

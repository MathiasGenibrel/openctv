package com.openctv.browser.core.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text

val SidePanelWidth: Dp = 380.dp

/**
 * Panneau latéral droit (réglages, menu) : `surfaceContainerHigh`, coins Extra-large.
 * Retour appelle [onDismiss] : le panneau se ferme avant toute autre action de Retour.
 * L'appelant place le focus initial sur un élément du contenu.
 */
@Composable
fun SidePanel(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 24.dp,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Surface(
        modifier = modifier.width(SidePanelWidth).fillMaxHeight(),
        shape = OpenCtvShapes.ExtraLarge,
        colors = SurfaceDefaults.colors(
            containerColor = OpenCtvColors.SurfaceContainerHigh,
            contentColor = OpenCtvColors.OnSurface,
        ),
    ) {
        Column(modifier = Modifier.focusGroup().padding(horizontal = 24.dp, vertical = verticalPadding)) { content() }
    }
}

/**
 * [SidePanel] en surcouche modale de l'écran resté vivant dessous : voile, panneau calé à droite dans la zone
 * sûre, et piège à focus ([focusTrap]) — le D-pad ne peut pas rejoindre l'écran dessous, qui n'a donc pas à
 * désactiver son propre focus. Retour appelle [onDismiss].
 */
@Composable
fun SidePanelOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 24.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize().focusTrap()) {
        Box(Modifier.fillMaxSize().background(OpenCtvColors.Scrim.copy(alpha = 0.5f)))
        OverscanSafeArea {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                SidePanel(onDismiss = onDismiss, verticalPadding = verticalPadding, content = content)
            }
        }
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun SidePanelPreview() {
    OpenCtvTheme {
        SidePanel(onDismiss = {}) {
            Text("Page de démarrage", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

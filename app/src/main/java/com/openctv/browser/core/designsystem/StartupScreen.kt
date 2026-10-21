package com.openctv.browser.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.openctv.browser.R

/**
 * Premier écran du démarrage à froid : fond OpenCTV, nom et indicateur de chargement, affichés pendant la
 * lecture de la page de démarrage et l'initialisation de la WebView (jamais d'écran noir). Rien n'y est
 * focalisable : il laisse la place à l'accueil ou au navigateur dès que la destination est connue.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StartupScreen(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(OpenCtvColors.Surface), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            ContainedLoadingIndicator(modifier = Modifier.size(96.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = OpenCtvColors.OnSurface,
            )
        }
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun StartupScreenPreview() {
    OpenCtvTheme { StartupScreen() }
}

package com.openctv.browser.features.home.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.openctv.browser.R
import com.openctv.browser.core.designsystem.OpeningKeyGuard
import com.openctv.browser.core.designsystem.OpenCtvButton
import com.openctv.browser.core.designsystem.OpenCtvButtonStyle
import com.openctv.browser.core.designsystem.OpenCtvColors
import com.openctv.browser.core.designsystem.OpenCtvShapes
import com.openctv.browser.core.designsystem.OpenCtvTheme
import com.openctv.browser.core.designsystem.focusTrap
import com.openctv.browser.core.designsystem.ignoresOpeningKey
import com.openctv.browser.features.home.domain.HomeFavorite

/**
 * Actions d'un favori (« Ouvrir » / « Retirer des favoris ») en petit panneau centré sur l'accueil assombri.
 * Il piège le focus (focus initial sur « Ouvrir », l'action sans risque) ; Retour appelle [onDismiss] et
 * l'accueil rend alors le focus à la tuile. Le OK maintenu qui l'a ouvert ne clique pas « Ouvrir » en étant
 * relâché ([OpeningKeyGuard]).
 */
@Composable
internal fun FavoriteMenu(
    favorite: HomeFavorite,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)
    val openFocus = remember { FocusRequester() }
    LaunchedEffect(favorite) { openFocus.requestFocus() }
    val openingKeyGuard = remember(favorite) { OpeningKeyGuard() }

    Box(modifier.fillMaxSize().ignoresOpeningKey(openingKeyGuard).focusTrap(), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().background(OpenCtvColors.Scrim.copy(alpha = 0.6f)))
        Column(
            Modifier
                .width(360.dp)
                .background(OpenCtvColors.SurfaceContainerHigh, OpenCtvShapes.ExtraLarge)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = favorite.name,
                style = MaterialTheme.typography.headlineSmall,
                color = OpenCtvColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = favorite.domain,
                style = MaterialTheme.typography.bodyMedium,
                color = OpenCtvColors.OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            OpenCtvButton(onClick = onOpen, modifier = Modifier.focusRequester(openFocus)) {
                Text(stringResource(R.string.home_favorite_open))
            }
            OpenCtvButton(onClick = onRemove, style = OpenCtvButtonStyle.Tonal) {
                Text(stringResource(R.string.home_favorite_remove))
            }
        }
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun FavoriteMenuPreview() {
    OpenCtvTheme {
        FavoriteMenu(HomeFavorite("https://arte.tv", "Arte", "arte.tv", "A", 0), {}, {}, {})
    }
}

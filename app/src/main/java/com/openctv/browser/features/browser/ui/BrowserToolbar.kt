package com.openctv.browser.features.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.openctv.browser.R
import com.openctv.browser.core.designsystem.OpenCtvColors
import com.openctv.browser.core.designsystem.OpenCtvIconButton
import com.openctv.browser.core.designsystem.OpenCtvIcons
import com.openctv.browser.core.designsystem.OpenCtvShapes
import com.openctv.browser.core.designsystem.OpenCtvTheme
import com.openctv.browser.core.designsystem.focusRingBorder
import com.openctv.browser.features.browser.domain.ReloadAction
import com.openctv.browser.features.browser.domain.ToolbarState

/** Actions de la barre d'outils : la vue les transmet au ViewModel, qui décide. */
data class ToolbarActions(
    val onBack: () -> Unit,
    val onForward: () -> Unit,
    val onReloadOrStop: () -> Unit,
    val onEditAddress: () -> Unit,
    val onToggleBookmark: () -> Unit,
    val onMenu: () -> Unit,
)

/**
 * Barre d'outils flottante « Vibrant » (`primaryContainer`, forme Full, élévation) : Précédent, Suivant,
 * Actualiser/Arrêter, champ d'adresse, Favori, Menu.
 *
 * [isInteractive] faux (mode page) : la barre reste affichée (chargement) mais ne prend jamais le focus,
 * le D-pad appartient à la page. [addressFocus] et [menuFocus] permettent à l'écran d'y poser le focus.
 */
@Composable
fun BrowserToolbar(
    state: ToolbarState,
    actions: ToolbarActions,
    isInteractive: Boolean,
    addressFocus: FocusRequester,
    menuFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = OpenCtvShapes.Full)
            .background(OpenCtvColors.PrimaryContainer, OpenCtvShapes.Full)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            // Pas de focusTarget sur la Row : chaque bouton remonte ses ancêtres jusqu'au premier focusTarget
            // et hérite de ce canFocus (le plus externe l'emporte). Ne pas ajouter de focusGroup ici.
            .focusProperties { canFocus = isInteractive },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OpenCtvIconButton(
            icon = OpenCtvIcons.ArrowBack,
            contentDescription = stringResource(R.string.browser_toolbar_back),
            onClick = actions.onBack,
            enabled = state.canGoBack,
        )
        OpenCtvIconButton(
            icon = OpenCtvIcons.ArrowForward,
            contentDescription = stringResource(R.string.browser_toolbar_forward),
            onClick = actions.onForward,
            enabled = state.canGoForward,
        )
        val isStop = state.reloadAction == ReloadAction.Stop
        OpenCtvIconButton(
            icon = if (isStop) OpenCtvIcons.Close else OpenCtvIcons.Refresh,
            contentDescription = stringResource(
                if (isStop) R.string.browser_toolbar_stop else R.string.browser_toolbar_reload,
            ),
            onClick = actions.onReloadOrStop,
        )
        AddressField(
            state = state,
            onClick = actions.onEditAddress,
            modifier = Modifier.weight(1f).focusRequester(addressFocus),
        )
        OpenCtvIconButton(
            icon = if (state.isBookmarked) OpenCtvIcons.StarFilled else OpenCtvIcons.Star,
            contentDescription = stringResource(
                if (state.isBookmarked) R.string.browser_toolbar_bookmark_remove else R.string.browser_toolbar_bookmark_add,
            ),
            onClick = actions.onToggleBookmark,
        )
        OpenCtvIconButton(
            icon = OpenCtvIcons.MoreVert,
            contentDescription = stringResource(R.string.browser_toolbar_menu),
            onClick = actions.onMenu,
            modifier = Modifier.focusRequester(menuFocus),
        )
    }
}

@Composable
private fun AddressField(state: ToolbarState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = OpenCtvShapes.Full
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape, focusedShape = shape),
        border = ClickableSurfaceDefaults.border(focusedBorder = focusRingBorder(shape)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = OpenCtvColors.SurfaceContainer,
            contentColor = OpenCtvColors.OnSurface,
            focusedContainerColor = OpenCtvColors.SurfaceContainer,
            focusedContentColor = OpenCtvColors.OnSurface,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isSecure) {
                Icon(OpenCtvIcons.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Text(
                text = state.site,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.browser_toolbar_address_hint),
                style = MaterialTheme.typography.labelLarge,
                color = OpenCtvColors.OnSurfaceVariant,
            )
        }
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun BrowserToolbarPreview() {
    OpenCtvTheme {
        Box(Modifier.padding(48.dp)) {
            BrowserToolbar(
                state = ToolbarState(
                    canGoBack = true,
                    canGoForward = false,
                    reloadAction = ReloadAction.Reload,
                    site = "arte.tv",
                    isSecure = true,
                    isBookmarked = true,
                ),
                actions = ToolbarActions({}, {}, {}, {}, {}, {}),
                isInteractive = true,
                addressFocus = FocusRequester(),
                menuFocus = FocusRequester(),
            )
        }
    }
}

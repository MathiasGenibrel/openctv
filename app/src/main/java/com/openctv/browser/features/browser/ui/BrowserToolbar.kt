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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import com.openctv.browser.features.browser.domain.PageInsecurity
import com.openctv.browser.features.browser.domain.ReloadAction
import com.openctv.browser.features.browser.domain.ToolbarButton
import com.openctv.browser.features.browser.domain.ToolbarState
import com.openctv.browser.features.browser.domain.focusFallbackOf

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
    val backFocus = remember { FocusRequester() }
    val forwardFocus = remember { FocusRequester() }
    val reloadFocus = remember { FocusRequester() }
    // Dernier bouton à avoir pris le focus : s'il devient inactif (Suivant sur la dernière page), le focus
    // passe au voisin actif au lieu de se perdre. Les autres éléments l'effacent en prenant le focus.
    var lastFocused by remember { mutableStateOf<ToolbarButton?>(null) }
    fun trackFocus(button: ToolbarButton?) = Modifier.onFocusChanged { if (it.isFocused) lastFocused = button }
    // Seul un changement d'historique déclenche le repli : l'entrée en mode interface reste à BrowserFocus, qui
    // pose le focus sur le champ d'adresse (sinon un `lastFocused` d'une visite précédente le lui disputerait).
    val currentIsInteractive by rememberUpdatedState(isInteractive)
    LaunchedEffect(state.canGoBack, state.canGoForward) {
        val fallback = if (currentIsInteractive) focusFallbackOf(lastFocused, state) else null
        when (fallback) {
            ToolbarButton.Back -> backFocus
            ToolbarButton.Forward -> forwardFocus
            ToolbarButton.Reload -> reloadFocus
            null -> null
        }?.let { runCatching { it.requestFocus() } }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = OpenCtvShapes.Full)
            .background(OpenCtvColors.PrimaryContainer, OpenCtvShapes.Full)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            // S'applique aux premiers focusTarget en dessous : les boutons (pas de focusGroup entre la Row et eux,
            // sinon seul le groupe serait concerné). On ne fait que retirer le focus, jamais `canFocus = true`, qui
            // rendrait focalisable un conteneur non focalisable et lui ferait avaler Retour (cf. conventions).
            .focusProperties { if (!isInteractive) canFocus = false },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OpenCtvIconButton(
            icon = OpenCtvIcons.ArrowBack,
            contentDescription = stringResource(R.string.browser_toolbar_back),
            onClick = actions.onBack,
            enabled = state.canGoBack,
            modifier = Modifier.focusRequester(backFocus).then(trackFocus(ToolbarButton.Back)),
        )
        OpenCtvIconButton(
            icon = OpenCtvIcons.ArrowForward,
            contentDescription = stringResource(R.string.browser_toolbar_forward),
            onClick = actions.onForward,
            enabled = state.canGoForward,
            modifier = Modifier.focusRequester(forwardFocus).then(trackFocus(ToolbarButton.Forward)),
        )
        val isStop = state.reloadAction == ReloadAction.Stop
        OpenCtvIconButton(
            icon = if (isStop) OpenCtvIcons.Close else OpenCtvIcons.Refresh,
            contentDescription = stringResource(
                if (isStop) R.string.browser_toolbar_stop else R.string.browser_toolbar_reload,
            ),
            onClick = actions.onReloadOrStop,
            modifier = Modifier.focusRequester(reloadFocus).then(trackFocus(ToolbarButton.Reload)),
        )
        AddressField(
            state = state,
            onClick = actions.onEditAddress,
            modifier = Modifier.weight(1f).focusRequester(addressFocus).then(trackFocus(null)),
        )
        OpenCtvIconButton(
            icon = if (state.isBookmarked) OpenCtvIcons.StarFilled else OpenCtvIcons.Star,
            contentDescription = stringResource(
                if (state.isBookmarked) R.string.browser_toolbar_bookmark_remove else R.string.browser_toolbar_bookmark_add,
            ),
            onClick = actions.onToggleBookmark,
            modifier = trackFocus(null),
        )
        OpenCtvIconButton(
            icon = OpenCtvIcons.MoreVert,
            contentDescription = stringResource(R.string.browser_toolbar_menu),
            onClick = actions.onMenu,
            modifier = Modifier.focusRequester(menuFocus).then(trackFocus(null)),
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
        // Pas d'agrandissement : le champ occupe déjà toute la largeur libre et un zoom recouvrirait
        // Actualiser et Favori. L'anneau de focus (3 dp + 2 dp d'écart) tient dans l'espacement de 8 dp.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isSecure) {
                Icon(OpenCtvIcons.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            state.insecurity?.let { InsecurityBadge(it) }
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

/** « Non sécurisé » (HTTP, `tertiary`) ou « Certificat non vérifié » (`error`) : page affichée par exception. */
@Composable
private fun InsecurityBadge(insecurity: PageInsecurity) {
    val isHttp = insecurity == PageInsecurity.Http
    val color = if (isHttp) OpenCtvColors.Tertiary else OpenCtvColors.Error
    val icon = if (isHttp) OpenCtvIcons.NoEncryption else OpenCtvIcons.GppBad
    val label = stringResource(if (isHttp) R.string.security_toolbar_insecure else R.string.security_toolbar_unverified)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun BrowserToolbarInsecurePreview() {
    OpenCtvTheme {
        Box(Modifier.padding(48.dp)) {
            BrowserToolbar(
                state = ToolbarState(
                    canGoBack = true, canGoForward = false, reloadAction = ReloadAction.Reload,
                    site = "192.168.1.10", isSecure = false, isBookmarked = false, insecurity = PageInsecurity.Http,
                ),
                actions = ToolbarActions({}, {}, {}, {}, {}, {}),
                isInteractive = true,
                addressFocus = FocusRequester(),
                menuFocus = FocusRequester(),
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

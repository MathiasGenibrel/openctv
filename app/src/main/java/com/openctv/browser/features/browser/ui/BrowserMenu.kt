package com.openctv.browser.features.browser.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.openctv.browser.R
import com.openctv.browser.core.designsystem.ListGroup
import com.openctv.browser.core.designsystem.ListGroupStyle
import com.openctv.browser.core.designsystem.ListNavigationRow
import com.openctv.browser.core.designsystem.ListRadioOptions
import com.openctv.browser.core.designsystem.ListRow
import com.openctv.browser.core.designsystem.ListSwitchRow
import com.openctv.browser.core.designsystem.OpenCtvColors
import com.openctv.browser.core.designsystem.OpenCtvIcons
import com.openctv.browser.core.designsystem.OpenCtvTheme
import com.openctv.browser.core.designsystem.SidePanelOverlay
import com.openctv.browser.core.zoom.PageZoom
import com.openctv.browser.core.zoom.percentLabel
import com.openctv.browser.core.zoom.widthLabel
import com.openctv.browser.features.browser.domain.AdBlockStatus
import com.openctv.browser.features.browser.domain.BrowserMenuPanel
import com.openctv.browser.features.browser.domain.BrowserMenuState
import com.openctv.browser.features.settings.domain.CursorSpeed
import com.openctv.browser.features.settings.domain.SearchEngine

/**
 * Menu du navigateur : panneau latéral droit sur la page assombrie, qui piège le focus. Retour ferme le
 * sous-panneau ouvert, puis le menu ([onClose]) ; le focus revient alors au bouton Menu (cf. BrowserFocus).
 */
@Composable
fun BrowserMenu(
    viewModel: BrowserMenuViewModel,
    onHome: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BrowserMenuContent(
        state = state,
        actions = MenuActions(
            onToggleAdBlock = viewModel::toggleAdBlock,
            onToggleBookmark = viewModel::toggleBookmark,
            onHome = onHome,
            onOpenPanel = viewModel::openPanel,
            onSelectPageZoom = { zoom ->
                // Le navigateur applique et mémorise le choix ; le menu se ferme pour laisser voir la page et le message.
                viewModel.selectPageZoom(zoom)
                viewModel.closePanel()
                onClose()
            },
            onSelectSearchEngine = viewModel::selectSearchEngine,
            onSelectCursorSpeed = viewModel::selectCursorSpeed,
        ),
        onBack = { if (!viewModel.onBack()) onClose() },
        modifier = modifier,
    )
}

internal class MenuActions(
    val onToggleAdBlock: () -> Unit,
    val onToggleBookmark: () -> Unit,
    val onHome: () -> Unit,
    val onOpenPanel: (BrowserMenuPanel) -> Unit,
    val onSelectPageZoom: (PageZoom) -> Unit,
    val onSelectSearchEngine: (SearchEngine) -> Unit,
    val onSelectCursorSpeed: (CursorSpeed) -> Unit,
)

@Composable
internal fun BrowserMenuContent(
    state: BrowserMenuState,
    actions: MenuActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hissé ici : survit au remplacement de la liste par un sous-panneau, pour rendre le focus à la bonne ligne.
    val rowFocus = remember { MenuFocus() }
    SidePanelOverlay(onDismiss = onBack, modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            val panel = state.openPanel
            if (panel == null) MenuList(state, actions, rowFocus) else MenuPanel(panel, state, actions)
        }
    }
}

@Composable
private fun ColumnScope.MenuList(state: BrowserMenuState, actions: MenuActions, rowFocus: MenuFocus) {
    // Focus initial sur le premier élément ; au retour d'un sous-panneau, sur la ligne qui l'a ouvert.
    LaunchedEffect(Unit) { rowFocus.restore() }

    Text(stringResource(R.string.browser_menu_title), style = MaterialTheme.typography.headlineSmall)
    Text(state.site, style = MaterialTheme.typography.bodyLarge, color = OpenCtvColors.OnSurfaceVariant)
    // Les groupes défilent si l'écran est trop bas ; le padding laisse la place à l'anneau de focus.
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ListGroup(style = ListGroupStyle.Vibrant) {
            ListSwitchRow(
                icon = OpenCtvIcons.Shield,
                label = stringResource(R.string.browser_menu_ad_block),
                supporting = adBlockSummary(state.adBlock),
                isChecked = state.isAdBlockActive,
                onClick = actions.onToggleAdBlock,
                modifier = Modifier.focusRequester(rowFocus.adBlock),
            )
            val bookmarkLabel = if (state.isBookmarked) R.string.browser_toolbar_bookmark_remove else R.string.browser_toolbar_bookmark_add
            ListRow(
                icon = if (state.isBookmarked) OpenCtvIcons.StarFilled else OpenCtvIcons.Star,
                label = stringResource(bookmarkLabel),
                supporting = state.site,
                onClick = actions.onToggleBookmark,
            )
            ListNavigationRow(
                icon = OpenCtvIcons.ZoomIn,
                label = stringResource(R.string.browser_menu_zoom),
                value = state.pageZoom.percentLabel(),
                onClick = { rowFocus.open(BrowserMenuPanel.PageZoom, actions.onOpenPanel) },
                modifier = Modifier.focusRequester(rowFocus.of(BrowserMenuPanel.PageZoom)),
            )
        }
        ListGroup(style = ListGroupStyle.Vibrant) {
            ListRow(OpenCtvIcons.Home, stringResource(R.string.browser_menu_home), onClick = actions.onHome)
        }
        ListGroup(style = ListGroupStyle.Vibrant) {
            ListNavigationRow(
                icon = OpenCtvIcons.TravelExplore,
                label = stringResource(R.string.settings_search_engine),
                value = state.searchEngine.displayName,
                onClick = { rowFocus.open(BrowserMenuPanel.SearchEngine, actions.onOpenPanel) },
                modifier = Modifier.focusRequester(rowFocus.of(BrowserMenuPanel.SearchEngine)),
            )
            ListNavigationRow(
                icon = OpenCtvIcons.Speed,
                label = stringResource(R.string.settings_cursor_speed),
                value = stringResource(state.cursorSpeed.labelRes()),
                onClick = { rowFocus.open(BrowserMenuPanel.CursorSpeed, actions.onOpenPanel) },
                modifier = Modifier.focusRequester(rowFocus.of(BrowserMenuPanel.CursorSpeed)),
            )
        }
    }
    Text(
        stringResource(R.string.browser_menu_back_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = OpenCtvColors.OnSurfaceVariant,
    )
}

/** Cibles de focus de la liste : la première ligne au départ, la ligne du dernier sous-panneau ensuite. */
private class MenuFocus {
    val adBlock = FocusRequester()
    private val panelRows = BrowserMenuPanel.entries.associateWith { FocusRequester() }
    private var lastOpened: BrowserMenuPanel? = null

    fun of(panel: BrowserMenuPanel): FocusRequester = panelRows.getValue(panel)

    fun open(panel: BrowserMenuPanel, onOpen: (BrowserMenuPanel) -> Unit) {
        lastOpened = panel
        onOpen(panel)
    }

    fun restore() {
        val target = lastOpened?.let(::of) ?: adBlock
        target.requestFocus()
    }
}

@Composable
private fun ColumnScope.MenuPanel(panel: BrowserMenuPanel, state: BrowserMenuState, actions: MenuActions) {
    val defaultBadge = stringResource(R.string.settings_default_badge)
    when (panel) {
        BrowserMenuPanel.PageZoom -> MenuOptions(
            title = stringResource(R.string.browser_menu_zoom),
            subtitle = state.zoomHost?.let { stringResource(R.string.browser_menu_zoom_remembered_for, it) },
            options = PageZoom.entries,
            selected = state.pageZoom,
            label = { it.percentLabel() },
            supporting = { it.widthLabel() },
            trailingText = { zoom -> defaultBadge.takeIf { zoom == state.defaultZoom } },
            note = stringResource(R.string.browser_menu_zoom_note),
            hint = stringResource(R.string.browser_menu_zoom_hint),
            onSelect = actions.onSelectPageZoom,
        )
        BrowserMenuPanel.SearchEngine -> MenuOptions(
            title = stringResource(R.string.settings_search_engine),
            options = SearchEngine.entries,
            selected = state.searchEngine,
            label = { it.displayName },
            supporting = { engine ->
                listOfNotNull(
                    defaultBadge.takeIf { engine == SearchEngine.Default },
                    stringResource(R.string.settings_no_tracking).takeIf { engine.isTrackingFree },
                ).joinToString(" · ").ifEmpty { null }
            },
            onSelect = actions.onSelectSearchEngine,
        )
        BrowserMenuPanel.CursorSpeed -> MenuOptions(
            title = stringResource(R.string.settings_cursor_speed),
            options = CursorSpeed.entries,
            selected = state.cursorSpeed,
            label = { stringResource(it.labelRes()) },
            supporting = { speed -> defaultBadge.takeIf { speed == CursorSpeed.Default } },
            onSelect = actions.onSelectCursorSpeed,
        )
    }
}

/** Radios d'un sous-panneau : le focus initial est sur l'option sélectionnée. */
@Composable
private fun <T> ColumnScope.MenuOptions(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    supporting: @Composable (T) -> String?,
    onSelect: (T) -> Unit,
    subtitle: String? = null,
    trailingText: @Composable (T) -> String? = { null },
    note: String? = null,
    hint: String = stringResource(R.string.browser_menu_back_hint),
) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = OpenCtvColors.OnSurfaceVariant) }
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ListGroup(style = ListGroupStyle.Vibrant) {
            ListRadioOptions(options, selected, label, supporting, onSelect, trailingText)
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = OpenCtvColors.OnSurfaceVariant) }
    }
    Text(
        hint,
        style = MaterialTheme.typography.bodyMedium,
        color = OpenCtvColors.OnSurfaceVariant,
    )
}

@Composable
private fun adBlockSummary(status: AdBlockStatus): String = when (status) {
    is AdBlockStatus.Active ->
        pluralStringResource(R.plurals.browser_menu_ad_block_count, status.blockedCount, status.blockedCount)
    AdBlockStatus.DisabledForSite -> stringResource(R.string.browser_menu_ad_block_site_off)
    AdBlockStatus.DisabledEverywhere -> stringResource(R.string.browser_menu_ad_block_global_off)
}

@StringRes
private fun CursorSpeed.labelRes(): Int = when (this) {
    CursorSpeed.Slow -> R.string.settings_cursor_slow
    CursorSpeed.Normal -> R.string.settings_cursor_normal
    CursorSpeed.Fast -> R.string.settings_cursor_fast
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun BrowserMenuPreview() {
    OpenCtvTheme {
        BrowserMenuContent(
            state = BrowserMenuState(site = "arte.tv", adBlock = AdBlockStatus.Active(12)),
            actions = MenuActions({}, {}, {}, {}, {}, {}, {}),
            onBack = {},
        )
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun BrowserMenuZoomPreview() {
    OpenCtvTheme {
        BrowserMenuContent(
            state = BrowserMenuState(
                site = "gitlab-dashboard.tools.internal",
                pageZoom = PageZoom.Half,
                zoomHost = "gitlab-dashboard.tools.internal",
                openPanel = BrowserMenuPanel.PageZoom,
            ),
            actions = MenuActions({}, {}, {}, {}, {}, {}, {}),
            onBack = {},
        )
    }
}

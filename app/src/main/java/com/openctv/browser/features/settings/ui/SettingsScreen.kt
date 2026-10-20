package com.openctv.browser.features.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.openctv.browser.R
import com.openctv.browser.core.designsystem.ListGroup
import com.openctv.browser.core.designsystem.ListNavigationRow
import com.openctv.browser.core.designsystem.ListRadioOptions
import com.openctv.browser.core.designsystem.ListRadioRow
import com.openctv.browser.core.designsystem.ListRow
import com.openctv.browser.core.designsystem.ListSwitchRow
import com.openctv.browser.core.designsystem.OpenCtvColors
import com.openctv.browser.core.designsystem.OpenCtvIconButton
import com.openctv.browser.core.designsystem.OpenCtvIcons
import com.openctv.browser.core.designsystem.OpenCtvShapes
import com.openctv.browser.core.designsystem.OpenCtvTheme
import com.openctv.browser.core.designsystem.OverscanSafeArea
import com.openctv.browser.core.designsystem.SidePanel
import com.openctv.browser.core.designsystem.focusRing
import com.openctv.browser.core.designsystem.focusRingBorder
import com.openctv.browser.features.bookmarks.domain.Bookmark
import com.openctv.browser.features.settings.domain.CursorSpeed
import com.openctv.browser.features.settings.domain.HudHideDelay
import com.openctv.browser.features.settings.domain.SearchEngine
import com.openctv.browser.features.settings.domain.Settings
import com.openctv.browser.features.settings.domain.StartPage
import java.time.Instant

/** Écran Réglages. Retour ferme le sous-panneau ouvert, puis appelle [onClose] (accueil). */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    filterLists: FilterListsStatus = FilterListsStatus(),
    onRefreshFilterLists: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler { if (!viewModel.onBack()) onClose() }

    SettingsContent(
        state = state,
        onClose = onClose,
        onOpenPanel = viewModel::open,
        onClosePanel = viewModel::closePanel,
        onSelectStartPage = viewModel::selectStartPage,
        onSelectStartSite = viewModel::selectStartSite,
        onSelectSearchEngine = viewModel::selectSearchEngine,
        onSelectHudHideDelay = viewModel::selectHudHideDelay,
        onSelectCursorSpeed = viewModel::selectCursorSpeed,
        onAdBlockChange = viewModel::setAdBlockEnabled,
        filterLists = filterLists,
        onRefreshFilterLists = onRefreshFilterLists,
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    onClose: () -> Unit,
    onOpenPanel: (SettingsPanel) -> Unit,
    onClosePanel: () -> Unit,
    onSelectStartPage: (StartPage) -> Unit,
    onSelectStartSite: (String) -> Unit,
    onSelectSearchEngine: (SearchEngine) -> Unit,
    onSelectHudHideDelay: (HudHideDelay) -> Unit,
    onSelectCursorSpeed: (CursorSpeed) -> Unit,
    onAdBlockChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    filterLists: FilterListsStatus = FilterListsStatus(),
    onRefreshFilterLists: () -> Unit = {},
) {
    val rowFocus = remember { SettingsPanel.entries.associateWith { FocusRequester() } }
    val isPanelOpen = state.openPanel != null

    // Focus initial sur la première ligne ; à la fermeture d'un panneau, retour sur la ligne qui l'a ouvert.
    var lastOpenedPanel by remember { mutableStateOf(SettingsPanel.StartPage) }
    state.openPanel?.let { lastOpenedPanel = it }
    LaunchedEffect(isPanelOpen) {
        if (!isPanelOpen) rowFocus.getValue(lastOpenedPanel).requestFocus()
    }

    Box(modifier = modifier.fillMaxSize().background(OpenCtvColors.Surface)) {
        OverscanSafeArea {
            Row(
                // Panneau ouvert : la liste derrière ne reçoit plus le focus.
                modifier = Modifier.fillMaxSize().focusProperties { canFocus = !isPanelOpen },
                horizontalArrangement = Arrangement.spacedBy(48.dp),
            ) {
                IntroColumn(isSaveFailed = state.isSaveFailed, onClose = onClose, modifier = Modifier.weight(0.4f))
                SettingsList(
                    settings = state.settings,
                    rowFocus = rowFocus,
                    onOpenPanel = onOpenPanel,
                    onAdBlockChange = onAdBlockChange,
                    filterLists = filterLists,
                    onRefreshFilterLists = onRefreshFilterLists,
                    modifier = Modifier.weight(0.6f),
                )
            }
        }
        state.openPanel?.let { panel ->
            Box(Modifier.fillMaxSize().background(OpenCtvColors.Scrim.copy(alpha = 0.5f)))
            OverscanSafeArea {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                    SidePanel(onDismiss = onClosePanel) {
                        PanelContent(
                            panel = panel,
                            state = state,
                            onSelectStartPage = onSelectStartPage,
                            onSelectStartSite = onSelectStartSite,
                            onSelectSearchEngine = onSelectSearchEngine,
                            onSelectHudHideDelay = onSelectHudHideDelay,
                            onSelectCursorSpeed = onSelectCursorSpeed,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroColumn(isSaveFailed: Boolean, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        OpenCtvIconButton(
            icon = OpenCtvIcons.ArrowBack,
            contentDescription = stringResource(R.string.settings_back),
            onClick = onClose,
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.displaySmall,
            color = OpenCtvColors.OnSurface,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_privacy_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = OpenCtvColors.OnSurfaceVariant,
        )
        if (isSaveFailed) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_save_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = OpenCtvColors.Error,
            )
        }
    }
}

@Composable
private fun SettingsList(
    settings: Settings,
    rowFocus: Map<SettingsPanel, FocusRequester>,
    onOpenPanel: (SettingsPanel) -> Unit,
    onAdBlockChange: (Boolean) -> Unit,
    filterLists: FilterListsStatus,
    onRefreshFilterLists: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        ListGroup {
            ListNavigationRow(
                icon = OpenCtvIcons.Home,
                label = stringResource(R.string.settings_start_page),
                value = startPageSummary(settings.startPage),
                onClick = { onOpenPanel(SettingsPanel.StartPage) },
                modifier = Modifier.focusRequester(rowFocus.getValue(SettingsPanel.StartPage)),
            )
            ListNavigationRow(
                icon = OpenCtvIcons.TravelExplore,
                label = stringResource(R.string.settings_search_engine),
                value = settings.searchEngine.displayName,
                onClick = { onOpenPanel(SettingsPanel.SearchEngine) },
                modifier = Modifier.focusRequester(rowFocus.getValue(SettingsPanel.SearchEngine)),
            )
        }
        ListGroup {
            ListNavigationRow(
                icon = OpenCtvIcons.Timer,
                label = stringResource(R.string.settings_hud_hide_delay),
                value = stringResource(settings.hudHideDelay.labelRes()),
                onClick = { onOpenPanel(SettingsPanel.HudHideDelay) },
                modifier = Modifier.focusRequester(rowFocus.getValue(SettingsPanel.HudHideDelay)),
            )
            ListNavigationRow(
                icon = OpenCtvIcons.Speed,
                label = stringResource(R.string.settings_cursor_speed),
                value = stringResource(settings.cursorSpeed.labelRes()),
                onClick = { onOpenPanel(SettingsPanel.CursorSpeed) },
                modifier = Modifier.focusRequester(rowFocus.getValue(SettingsPanel.CursorSpeed)),
            )
        }
        ListGroup {
            val adBlockValue = if (settings.isAdBlockEnabled) R.string.settings_ad_block_on else R.string.settings_ad_block_off
            ListSwitchRow(
                icon = OpenCtvIcons.Shield,
                label = stringResource(R.string.settings_ad_block),
                supporting = stringResource(adBlockValue),
                isChecked = settings.isAdBlockEnabled,
                onClick = { onAdBlockChange(!settings.isAdBlockEnabled) },
            )
            // OK = mettre à jour maintenant ; sans effet pendant une mise à jour (le focus reste sur la ligne).
            ListRow(
                icon = OpenCtvIcons.FilterList,
                label = stringResource(R.string.settings_filter_lists),
                supporting = filterListsSummary(filterLists),
                onClick = { if (!filterLists.isUpdating) onRefreshFilterLists() },
            )
        }
    }
}

@Composable
private fun filterListsSummary(status: FilterListsStatus): String = when (val summary = status.summary(Instant.now())) {
    FilterListsSummary.Updating -> stringResource(R.string.settings_filter_lists_updating)
    FilterListsSummary.Failed -> stringResource(R.string.settings_filter_lists_failed)
    FilterListsSummary.Never -> stringResource(R.string.settings_filter_lists_never)
    FilterListsSummary.Today -> stringResource(R.string.settings_filter_lists_today)
    is FilterListsSummary.DaysAgo ->
        pluralStringResource(R.plurals.settings_filter_lists_days_ago, summary.days, summary.days)
}

@Composable
private fun startPageSummary(page: StartPage): String = when (page) {
    is StartPage.Site -> stringResource(R.string.settings_start_site) + " : " + page.url.removePrefix("https://").removePrefix("http://")
    else -> stringResource(page.labelRes())
}

@Composable
private fun PanelContent(
    panel: SettingsPanel,
    state: SettingsUiState,
    onSelectStartPage: (StartPage) -> Unit,
    onSelectStartSite: (String) -> Unit,
    onSelectSearchEngine: (SearchEngine) -> Unit,
    onSelectHudHideDelay: (HudHideDelay) -> Unit,
    onSelectCursorSpeed: (CursorSpeed) -> Unit,
) {
    val settings = state.settings
    val default = stringResource(R.string.settings_default_badge)
    when (panel) {
        SettingsPanel.StartPage -> StartPagePanel(state, onSelectStartPage, onSelectStartSite)
        SettingsPanel.SearchEngine -> PanelOptions(
            title = stringResource(R.string.settings_search_engine),
            options = SearchEngine.entries,
            selected = settings.searchEngine,
            label = { it.displayName },
            supporting = { engine ->
                listOfNotNull(
                    default.takeIf { engine == SearchEngine.Default },
                    stringResource(R.string.settings_no_tracking).takeIf { engine.isTrackingFree },
                ).joinToString(" · ").ifEmpty { null }
            },
            onSelect = onSelectSearchEngine,
        )
        SettingsPanel.HudHideDelay -> PanelOptions(
            title = stringResource(R.string.settings_hud_hide_delay),
            options = HudHideDelay.entries,
            selected = settings.hudHideDelay,
            label = { stringResource(it.labelRes()) },
            supporting = { option -> default.takeIf { option == HudHideDelay.Default } },
            onSelect = onSelectHudHideDelay,
        )
        SettingsPanel.CursorSpeed -> PanelOptions(
            title = stringResource(R.string.settings_cursor_speed),
            options = CursorSpeed.entries,
            selected = settings.cursorSpeed,
            label = { stringResource(it.labelRes()) },
            supporting = { option -> default.takeIf { option == CursorSpeed.Default } },
            onSelect = onSelectCursorSpeed,
        )
    }
}

/** Liste de radios : le focus initial est sur l'option sélectionnée. */
@Composable
private fun <T> PanelOptions(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    supporting: @Composable (T) -> String?,
    onSelect: (T) -> Unit,
) {
    PanelTitle(title)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ListRadioOptions(options, selected, label, supporting, onSelect)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartPagePanel(
    state: SettingsUiState,
    onSelectStartPage: (StartPage) -> Unit,
    onSelectStartSite: (String) -> Unit,
) {
    val current = state.settings.startPage
    val initialFocus = remember { FocusRequester() }
    val addressFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { initialFocus.requestFocus() }
    var address by remember { mutableStateOf("") }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        PanelTitle(stringResource(R.string.settings_start_page))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ListRadioRow(
                label = stringResource(R.string.settings_start_home),
                isSelected = current == StartPage.Home,
                onClick = { onSelectStartPage(StartPage.Home) },
                supporting = stringResource(R.string.settings_default_badge),
                modifier = Modifier.focusRequester(initialFocus),
            )
            ListRadioRow(
                label = stringResource(R.string.settings_start_last),
                isSelected = current == StartPage.LastPage,
                onClick = { onSelectStartPage(StartPage.LastPage) },
            )
            val site = current as? StartPage.Site
            ListRadioRow(
                label = stringResource(R.string.settings_start_site),
                isSelected = site != null,
                // Sans site déjà choisi, il n'y a rien à valider : on amène à la saisie.
                onClick = { if (site == null) addressFocus.requestFocus() },
                supporting = site?.url ?: stringResource(R.string.settings_start_site_unset),
            )
        }
        if (state.bookmarks.isNotEmpty()) {
            SectionLabel(stringResource(R.string.settings_start_from_bookmarks))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.bookmarks.forEach { bookmark ->
                    BookmarkChip(
                        bookmark = bookmark,
                        isSelected = (current as? StartPage.Site)?.url == bookmark.url,
                        onClick = { onSelectStartSite(bookmark.url) },
                    )
                }
            }
        }
        SectionLabel(stringResource(R.string.settings_start_type_address))
        AddressField(
            value = address,
            onValueChange = { address = it },
            onDone = { onSelectStartSite(address) },
            modifier = Modifier.focusRequester(addressFocus),
        )
        if (state.isSiteInputInvalid) {
            Text(
                text = stringResource(R.string.settings_start_address_invalid),
                style = MaterialTheme.typography.bodyMedium,
                color = OpenCtvColors.Error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun PanelTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        color = OpenCtvColors.OnSurface,
        modifier = Modifier.padding(bottom = 16.dp),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = OpenCtvColors.OnSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun BookmarkChip(bookmark: Bookmark, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape, focusedShape = CircleShape),
        border = ClickableSurfaceDefaults.border(focusedBorder = focusRingBorder(CircleShape)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) OpenCtvColors.SecondaryContainer else OpenCtvColors.SurfaceContainerHighest,
            contentColor = if (isSelected) OpenCtvColors.OnSecondaryContainer else OpenCtvColors.OnSurface,
            focusedContainerColor = OpenCtvColors.PrimaryContainer,
            focusedContentColor = OpenCtvColors.OnPrimaryContainer,
        ),
    ) {
        Text(
            text = bookmark.title,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AddressField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .focusRing(OpenCtvShapes.Medium)
            .background(OpenCtvColors.SurfaceContainerHighest, OpenCtvShapes.Medium)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = OpenCtvColors.OnSurface),
            cursorBrush = SolidColor(OpenCtvColors.Primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_start_address_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = OpenCtvColors.OnSurfaceVariant,
                    )
                }
                inner()
            },
        )
    }
}

private val PreviewBookmarks = listOf(
    Bookmark("https://arte.tv", "Arte", Instant.EPOCH),
    Bookmark("https://wikipedia.org", "Wikipedia", Instant.EPOCH),
)

@Preview(device = Devices.TV_1080p)
@Composable
private fun SettingsPreview() {
    OpenCtvTheme {
        SettingsContent(
            state = SettingsUiState(bookmarks = PreviewBookmarks),
            onClose = {}, onOpenPanel = {}, onClosePanel = {}, onSelectStartPage = {}, onSelectStartSite = {},
            onSelectSearchEngine = {}, onSelectHudHideDelay = {}, onSelectCursorSpeed = {}, onAdBlockChange = {},
        )
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun SettingsStartPagePanelPreview() {
    OpenCtvTheme {
        SettingsContent(
            state = SettingsUiState(
                settings = Settings(startPage = StartPage.Site("https://arte.tv")),
                bookmarks = PreviewBookmarks,
                openPanel = SettingsPanel.StartPage,
            ),
            onClose = {}, onOpenPanel = {}, onClosePanel = {}, onSelectStartPage = {}, onSelectStartSite = {},
            onSelectSearchEngine = {}, onSelectHudHideDelay = {}, onSelectCursorSpeed = {}, onAdBlockChange = {},
        )
    }
}

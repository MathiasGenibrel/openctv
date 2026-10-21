package com.openctv.browser.features.history.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.openctv.browser.R
import com.openctv.browser.core.designsystem.ExpressiveShape
import com.openctv.browser.core.designsystem.ListGroup
import com.openctv.browser.core.designsystem.ListRow
import com.openctv.browser.core.designsystem.OpenCtvButton
import com.openctv.browser.core.designsystem.OpenCtvButtonStyle
import com.openctv.browser.core.designsystem.OpenCtvColors
import com.openctv.browser.core.designsystem.OpenCtvIconButton
import com.openctv.browser.core.designsystem.OpenCtvIcons
import com.openctv.browser.core.designsystem.OpenCtvShapes
import com.openctv.browser.core.designsystem.OpenCtvTheme
import com.openctv.browser.core.designsystem.OverscanSafeArea
import com.openctv.browser.core.designsystem.asShape
import com.openctv.browser.core.designsystem.focusTrap
import com.openctv.browser.features.history.domain.DayLabel
import com.openctv.browser.features.history.domain.HistoryDay
import com.openctv.browser.features.history.domain.HistoryEntry
import com.openctv.browser.features.history.domain.HistoryFocus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Écran Historique, plein écran : destination (depuis l'accueil) ou surcouche du navigateur (depuis son menu), d'où
 * son piège à focus. [onOpen] ouvre la page choisie (l'écran d'origine applique les règles de sécurité habituelles) ;
 * [onClose] revient à l'écran d'origine. Retour ferme le dialogue de confirmation, puis l'écran.
 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val requestedFocus by viewModel.focus.collectAsStateWithLifecycle()

    BackHandler { if (!viewModel.onBack()) onClose() }

    HistoryContentScreen(
        state = state,
        requestedFocus = requestedFocus,
        onFocusHandled = viewModel::onFocusHandled,
        onOpen = { entry -> onOpen(entry.url) },
        onRemove = viewModel::remove,
        onClearAll = viewModel::requestClear,
        onConfirmClear = viewModel::confirmClear,
        onCancelClear = viewModel::cancelClear,
        onClose = onClose,
        modifier = modifier,
    )
}

@Composable
internal fun HistoryContentScreen(
    state: HistoryUiState,
    requestedFocus: HistoryFocus?,
    onFocusHandled: () -> Unit,
    onOpen: (HistoryEntry) -> Unit,
    onRemove: (HistoryEntry) -> Unit,
    onClearAll: () -> Unit,
    onConfirmClear: () -> Unit,
    onCancelClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { HistoryFocusTargets() }
    val listState = rememberLazyListState()
    val content = state.content
    val days = (content as? HistoryContent.Entries)?.days.orEmpty()
    val currentDays by rememberUpdatedState(days)

    // Le focus ne se perd jamais : le bouton retour d'abord (pendant la lecture), puis le premier élément utile.
    LaunchedEffect(Unit) { focus.backButton.requestFocus() }
    LaunchedEffect(content::class) {
        val entries = (content as? HistoryContent.Entries)?.entries.orEmpty()
        val isLoading = content is HistoryContent.Loading
        if (!isLoading) focus.request(HistoryFocus.initial(entries), currentDays, listState)
    }
    // Focus décidé par le ViewModel : après un retrait (entrée voisine) ou la fermeture du dialogue.
    LaunchedEffect(requestedFocus) {
        val target = requestedFocus ?: return@LaunchedEffect
        focus.request(target, currentDays, listState)
        onFocusHandled()
    }

    Box(modifier.fillMaxSize().background(OpenCtvColors.Surface).focusTrap()) {
        OverscanSafeArea {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                IntroColumn(
                    hasEntries = content is HistoryContent.Entries,
                    isWriteFailed = state.isWriteFailed,
                    focus = focus,
                    onClearAll = onClearAll,
                    onClose = onClose,
                    modifier = Modifier.weight(0.4f),
                )
                Box(Modifier.weight(0.6f).fillMaxSize()) {
                    when (content) {
                        HistoryContent.Loading -> Unit
                        HistoryContent.Unavailable -> Message(stringResource(R.string.history_unavailable))
                        HistoryContent.Empty -> EmptyHistory()
                        is HistoryContent.Entries -> HistoryList(content.days, focus, listState, onOpen, onRemove)
                    }
                }
            }
        }
        if (state.isClearConfirmationOpen) {
            val count = (content as? HistoryContent.Entries)?.count ?: 0
            ClearConfirmation(count = count, onCancel = onCancelClear, onConfirm = onConfirmClear)
        }
    }
}

/** Un [FocusRequester] par élément que l'écran peut focaliser. */
private class HistoryFocusTargets {
    val backButton = FocusRequester()
    val clearAll = FocusRequester()
    private val entries = mutableMapOf<Long, FocusRequester>()

    fun of(entryId: Long): FocusRequester = entries.getOrPut(entryId) { FocusRequester() }

    /**
     * Donne le focus à [target]. Une entrée hors de l'écran n'est pas composée (liste paresseuse) : on fait défiler
     * jusqu'à son jour d'abord. Une cible absente (retirée entre-temps) se rabat sur le bouton retour.
     */
    suspend fun request(target: HistoryFocus, days: List<HistoryDay>, listState: LazyListState) {
        val requester = when (target) {
            HistoryFocus.BackButton -> backButton
            HistoryFocus.ClearAll -> clearAll
            is HistoryFocus.Entry -> {
                val dayIndex = days.indexOfFirst { day -> day.entries.any { it.id == target.id } }
                if (dayIndex >= 0) listState.bringIntoView(dayIndex)
                if (dayIndex >= 0) of(target.id) else backButton
            }
        }
        runCatching { requester.requestFocus() }.onFailure { runCatching { backButton.requestFocus() } }
    }
}

private suspend fun LazyListState.bringIntoView(index: Int) {
    withFrameNanos { }
    val isVisible = layoutInfo.visibleItemsInfo.any { it.index == index }
    if (!isVisible) {
        scrollToItem(index)
        withFrameNanos { }
    }
}

@Composable
private fun IntroColumn(
    hasEntries: Boolean,
    isWriteFailed: Boolean,
    focus: HistoryFocusTargets,
    onClearAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            OpenCtvIconButton(
                icon = OpenCtvIcons.ArrowBack,
                contentDescription = stringResource(R.string.history_back),
                onClick = onClose,
                modifier = Modifier.focusRequester(focus.backButton),
            )
            Spacer(Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.displaySmall,
                color = OpenCtvColors.OnSurface,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.history_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = OpenCtvColors.OnSurfaceVariant,
            )
            if (isWriteFailed) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.history_write_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = OpenCtvColors.Error,
                )
            }
            if (hasEntries) {
                Spacer(Modifier.height(24.dp))
                OpenCtvButton(
                    onClick = onClearAll,
                    style = OpenCtvButtonStyle.OutlineError,
                    modifier = Modifier.focusRequester(focus.clearAll),
                ) {
                    Icon(OpenCtvIcons.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.history_clear_all))
                }
            }
        }
        Text(
            text = stringResource(if (hasEntries) R.string.history_help else R.string.history_empty_help),
            style = MaterialTheme.typography.bodyMedium,
            color = OpenCtvColors.OnSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

/** Un groupe par jour ; liste paresseuse (jusqu'à 1 000 entrées), marges internes pour l'anneau de focus. */
@Composable
private fun HistoryList(
    days: List<HistoryDay>,
    focus: HistoryFocusTargets,
    listState: LazyListState,
    onOpen: (HistoryEntry) -> Unit,
    onRemove: (HistoryEntry) -> Unit,
) {
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val zone = remember { ZoneId.systemDefault() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(days, key = { it.label.toString() }) { day ->
            Column {
                Text(
                    text = dayText(day.label),
                    style = MaterialTheme.typography.labelLarge,
                    color = OpenCtvColors.OnSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                )
                ListGroup {
                    day.entries.forEach { entry ->
                        key(entry.id) {
                            HistoryRow(
                                entry = entry,
                                time = timeFormat.format(entry.visitedAt.atZone(zone)),
                                requester = focus.of(entry.id),
                                onOpen = { onOpen(entry) },
                                onRemove = { onRemove(entry) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Ligne : OK ouvre la page ; Droite atteint l'icône de retrait (focalisable à part), dont OK retire l'entrée.
 */
@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    time: String,
    requester: FocusRequester,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ListRow(
            icon = OpenCtvIcons.Language,
            label = entry.label,
            isLabelSingleLine = true,
            supporting = stringResource(R.string.history_entry_supporting, entry.domain, time),
            onClick = onOpen,
            modifier = Modifier.weight(1f).focusRequester(requester),
        )
        OpenCtvIconButton(
            icon = OpenCtvIcons.Delete,
            contentDescription = stringResource(R.string.history_remove),
            onClick = onRemove,
            style = OpenCtvButtonStyle.Text,
        )
    }
}

@Composable
private fun dayText(label: DayLabel): String = when (label) {
    DayLabel.Today -> stringResource(R.string.history_today)
    DayLabel.Yesterday -> stringResource(R.string.history_yesterday)
    is DayLabel.On -> remember(label) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).format(label.date) }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = OpenCtvColors.OnSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyHistory() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(120.dp).background(OpenCtvColors.TertiaryContainer, ExpressiveShape.SoftBurst.asShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(OpenCtvIcons.History, contentDescription = null, modifier = Modifier.size(48.dp), tint = OpenCtvColors.OnTertiaryContainer)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.history_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            color = OpenCtvColors.OnSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.history_empty_text),
            style = MaterialTheme.typography.bodyMedium,
            color = OpenCtvColors.OnSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Dialogue modal : « Annuler » focalisé, Retour = Annuler ; le D-pad ne peut pas rejoindre la liste dessous. */
@Composable
private fun ClearConfirmation(count: Int, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    BackHandler(onBack = onCancel)

    Box(Modifier.fillMaxSize().focusTrap(), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().background(OpenCtvColors.Scrim.copy(alpha = 0.6f)))
        Column(
            modifier = Modifier
                .width(420.dp)
                .background(OpenCtvColors.SurfaceContainerHigh, OpenCtvShapes.ExtraLarge)
                .padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.history_clear_title),
                style = MaterialTheme.typography.headlineSmall,
                color = OpenCtvColors.OnSurface,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = pluralStringResource(R.plurals.history_clear_message, count, count),
                style = MaterialTheme.typography.bodyMedium,
                color = OpenCtvColors.OnSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OpenCtvButton(
                    onClick = onCancel,
                    style = OpenCtvButtonStyle.Text,
                    modifier = Modifier.focusRequester(cancelFocus),
                ) { Text(stringResource(R.string.history_clear_cancel)) }
                OpenCtvButton(onClick = onConfirm, style = OpenCtvButtonStyle.TextError) {
                    Text(stringResource(R.string.history_clear_confirm))
                }
            }
        }
    }
}

private val PreviewEntries = listOf(
    HistoryEntry(1, "https://www.arte.tv/fr/", "ARTE - Regarder des documentaires", Instant.parse("2026-10-21T18:42:00Z")),
    HistoryEntry(2, "https://fr.wikipedia.org/wiki/Android_TV", "Android TV - Wikipédia", Instant.parse("2026-10-21T17:10:00Z")),
    HistoryEntry(3, "https://duckduckgo.com/?q=openctv", "openctv at DuckDuckGo", Instant.parse("2026-10-20T20:05:00Z")),
)

private fun previewState(content: HistoryContent, isClearConfirmationOpen: Boolean = false) =
    HistoryUiState(content, isClearConfirmationOpen)

private fun previewDays(): List<HistoryDay> = listOf(
    HistoryDay(DayLabel.Today, PreviewEntries.take(2)),
    HistoryDay(DayLabel.Yesterday, PreviewEntries.drop(2)),
    HistoryDay(DayLabel.On(LocalDate.of(2026, 10, 18)), emptyList()),
).filter { it.entries.isNotEmpty() }

@Preview(device = Devices.TV_1080p)
@Composable
private fun HistoryPreview() {
    OpenCtvTheme {
        HistoryContentScreen(previewState(HistoryContent.Entries(previewDays())), null, {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun HistoryEmptyPreview() {
    OpenCtvTheme {
        HistoryContentScreen(previewState(HistoryContent.Empty), null, {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun HistoryConfirmationPreview() {
    OpenCtvTheme {
        HistoryContentScreen(previewState(HistoryContent.Entries(previewDays()), true), null, {}, {}, {}, {}, {}, {}, {})
    }
}

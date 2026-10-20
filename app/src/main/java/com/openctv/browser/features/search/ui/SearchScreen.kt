package com.openctv.browser.features.search.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.openctv.browser.core.designsystem.OverscanSafeArea
import com.openctv.browser.core.designsystem.focusRingBorder
import com.openctv.browser.core.designsystem.focusTrap
import com.openctv.browser.features.search.domain.SearchMode
import com.openctv.browser.features.search.domain.Suggestion

/**
 * Saisie d'adresse / recherche, affichée en **surcouche** plein écran de l'écran qui l'ouvre (accueil,
 * navigateur) : celui-ci reste vivant dessous, et le focus ne peut pas y fuir. Le clavier système s'ouvre
 * avec la saisie ; D-pad bas depuis le champ atteint les suggestions, OK sur l'une l'exécute.
 *
 * [onOpen] reçoit l'adresse à charger ; [onClose] ferme la surcouche (Retour, ou favori ajouté) et
 * l'écran d'origine rend le focus à l'élément qui l'avait ouverte.
 *
 * Retour avec le clavier système ouvert : le premier appui est consommé par l'IME (il ferme le clavier),
 * le second ferme la saisie. Comportement standard d'Android TV, conservé tel quel.
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onOpen: (url: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Champ local : porte la sélection (texte pré-rempli sélectionné d'un coup) ; le ViewModel garde le texte.
    var field by remember {
        val initial = viewModel.query.value
        mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length)))
    }

    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SearchEvent.Open -> currentOnOpen(event.url)
                SearchEvent.Close -> currentOnClose()
            }
        }
    }

    BackHandler(onBack = onClose)

    SearchContent(
        state = state,
        field = field,
        onFieldChange = {
            field = it
            viewModel.onQueryChange(it.text)
        },
        onClear = {
            field = TextFieldValue()
            viewModel.onQueryChange("")
        },
        onSubmit = viewModel::onSubmit,
        onBack = onClose,
        onSelect = viewModel::onSuggestionSelected,
        onClearRecents = viewModel::onClearRecents,
        modifier = modifier,
    )
}

@Composable
internal fun SearchContent(
    state: SearchUiState,
    field: TextFieldValue,
    onFieldChange: (TextFieldValue) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onSelect: (Suggestion) -> Unit,
    onClearRecents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Focus initial : le champ, avec le clavier système ouvert.
    LaunchedEffect(Unit) {
        fieldFocus.requestFocus()
        keyboard?.show()
    }

    // Fond opaque jusqu'aux bords (avant la marge d'overscan) : l'écran d'origine, vivant dessous, ne
    // transparaît pas ; le focus ne peut pas y fuir.
    OverscanSafeArea(modifier.background(OpenCtvColors.Surface).focusTrap()) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OpenCtvIconButton(
                    icon = OpenCtvIcons.ArrowBack,
                    contentDescription = stringResource(R.string.search_back),
                    onClick = onBack,
                )
                SearchField(
                    state = state,
                    field = field,
                    onFieldChange = onFieldChange,
                    onClear = onClear,
                    onSubmit = onSubmit,
                    fieldFocus = fieldFocus,
                    modifier = Modifier.weight(1f),
                )
            }
            state.problem?.let { Problem(it) }
            Spacer(Modifier.height(16.dp))
            Suggestions(state = state, onSelect = onSelect, onClearRecents = onClearRecents)
        }
    }
}

@Composable
private fun SearchField(
    state: SearchUiState,
    field: TextFieldValue,
    onFieldChange: (TextFieldValue) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    fieldFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val isAddingBookmark = state.mode == SearchMode.AddBookmark
    val placeholder = if (isAddingBookmark) {
        stringResource(R.string.search_add_placeholder)
    } else {
        stringResource(R.string.search_placeholder, state.engineName)
    }
    val imeAction = if (isAddingBookmark) ImeAction.Go else ImeAction.Search
    val isClearVisible = field.text.isNotEmpty()

    Row(
        modifier = modifier
            .height(56.dp)
            .border(2.dp, OpenCtvColors.Primary, OpenCtvShapes.Full)
            .background(OpenCtvColors.SurfaceContainerHigh, OpenCtvShapes.Full)
            .padding(start = 24.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(OpenCtvIcons.Search, contentDescription = null, tint = OpenCtvColors.OnSurfaceVariant)
        BasicTextField(
            value = field,
            onValueChange = onFieldChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = OpenCtvColors.OnSurface),
            cursorBrush = SolidColor(OpenCtvColors.Primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onSubmit() }),
            modifier = Modifier.weight(1f).focusRequester(fieldFocus),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (field.text.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = OpenCtvColors.OnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
        if (isClearVisible) {
            OpenCtvIconButton(
                icon = OpenCtvIcons.Close,
                contentDescription = stringResource(R.string.search_clear),
                onClick = onClear,
            )
        }
    }
}

@Composable
private fun Problem(problem: SearchProblem) {
    val message = when (problem) {
        SearchProblem.NotAnAddress -> stringResource(R.string.search_problem_not_an_address)
        SearchProblem.SaveFailed -> stringResource(R.string.search_problem_save_failed)
    }
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = OpenCtvColors.Error,
        modifier = Modifier.padding(start = 72.dp, top = 12.dp),
    )
}

@Composable
private fun Suggestions(
    state: SearchUiState,
    onSelect: (Suggestion) -> Unit,
    onClearRecents: () -> Unit,
) {
    val isEmpty = state.suggestions.isEmpty() && !state.canClearRecents
    if (isEmpty) return
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .background(OpenCtvColors.SurfaceContainer, OpenCtvShapes.ExtraLarge),
        // Marge : la ligne focalisée grandit et son anneau déborde, la liste ne doit pas le rogner.
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(state.suggestions, key = { it.key() }) { suggestion ->
            SuggestionRow(suggestion = suggestion, onClick = { onSelect(suggestion) })
        }
        if (state.canClearRecents) {
            item(key = "clear-recents") { ClearRecentsRow(onClick = onClearRecents) }
        }
    }
}

private fun Suggestion.key(): String = when (this) {
    is Suggestion.Favorite -> "favorite:$url"
    is Suggestion.Recent -> "recent:$query"
}

@Composable
private fun SuggestionRow(suggestion: Suggestion, onClick: () -> Unit) {
    val icon = when (suggestion) {
        is Suggestion.Favorite -> OpenCtvIcons.StarFilled
        is Suggestion.Recent -> OpenCtvIcons.History
    }
    val title = when (suggestion) {
        is Suggestion.Favorite -> suggestion.title
        is Suggestion.Recent -> suggestion.query
    }
    val action = when (suggestion) {
        is Suggestion.Favorite -> stringResource(R.string.search_favorite_action)
        is Suggestion.Recent -> stringResource(R.string.search_recent_action)
    }
    RowSurface(onClick = onClick) {
        Icon(icon, contentDescription = null)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(text = action, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ClearRecentsRow(onClick: () -> Unit) {
    RowSurface(onClick = onClick) {
        Icon(OpenCtvIcons.Close, contentDescription = null)
        Text(
            text = stringResource(R.string.search_clear_recents),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RowSurface(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val shape = OpenCtvShapes.Full
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape, focusedShape = shape),
        border = ClickableSurfaceDefaults.border(focusedBorder = focusRingBorder(shape)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = OpenCtvColors.SurfaceContainer,
            contentColor = OpenCtvColors.OnSurface,
            focusedContainerColor = OpenCtvColors.PrimaryContainer,
            focusedContentColor = OpenCtvColors.OnPrimaryContainer,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

private val PreviewState = SearchUiState(
    mode = SearchMode.Browse,
    engineName = "DuckDuckGo",
    suggestions = listOf(
        Suggestion.Favorite("https://arte.tv", "Arte"),
        Suggestion.Recent("arte documentaire"),
    ),
    canClearRecents = false,
    problem = null,
)

@Preview(device = Devices.TV_1080p)
@Composable
private fun SearchPreview() {
    OpenCtvTheme {
        SearchContent(
            state = PreviewState,
            field = TextFieldValue("arte"),
            onFieldChange = {}, onClear = {}, onSubmit = {}, onBack = {}, onSelect = {}, onClearRecents = {},
        )
    }
}

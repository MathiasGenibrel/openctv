package com.openctv.browser.features.search.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openctv.browser.core.common.Resource
import com.openctv.browser.features.bookmarks.domain.BookmarkUrl
import com.openctv.browser.features.bookmarks.domain.BookmarksContract
import com.openctv.browser.features.search.domain.InputResolver
import com.openctv.browser.features.search.domain.RecentSearchesContract
import com.openctv.browser.features.search.domain.ResolvedInput
import com.openctv.browser.features.search.domain.SearchMode
import com.openctv.browser.features.search.domain.SearchRequest
import com.openctv.browser.features.search.domain.Suggestion
import com.openctv.browser.features.search.domain.SuggestionBuilder
import com.openctv.browser.features.settings.domain.SettingsContract
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Raison pour laquelle la validation n'a pas abouti ; affichée sous la barre. */
enum class SearchProblem {
    /** Mode « ajouter un favori » : la saisie n'est pas une adresse de site. */
    NotAnAddress,

    /** Mode « ajouter un favori » : le stockage a échoué. */
    SaveFailed,
}

data class SearchUiState(
    val mode: SearchMode,
    val engineName: String,
    val suggestions: List<Suggestion>,
    /** Action « Effacer les recherches récentes » proposée (saisie vide et au moins une recherche stockée). */
    val canClearRecents: Boolean,
    val problem: SearchProblem?,
)

/**
 * Issue de la saisie, adressée à l'écran qui l'a ouverte : c'est lui qui décide où charger l'adresse
 * (navigateur ouvert depuis l'accueil, ou même WebView depuis la barre d'adresse).
 */
sealed interface SearchEvent {
    /** Adresse ou page de résultats à ouvrir (toujours `http`/`https`). */
    data class Open(val url: String) : SearchEvent

    /** Saisie terminée sans rien ouvrir (favori ajouté) : fermer la surcouche. */
    data object Close : SearchEvent
}

class SearchViewModel(
    request: SearchRequest,
    private val bookmarks: BookmarksContract,
    private val recents: RecentSearchesContract,
    private val settings: SettingsContract,
) : ViewModel() {
    private val mode: SearchMode = request.mode

    private val _query = MutableStateFlow(request.initialText)
    val query: StateFlow<String> = _query.asStateFlow()

    private val problem = MutableStateFlow<SearchProblem?>(null)

    private val _events = Channel<SearchEvent>(Channel.BUFFERED)
    val events: Flow<SearchEvent> = _events.receiveAsFlow()

    private val storedBookmarks = bookmarks.getBookmarks().dataOrEmpty()
    private val storedRecents = recents.getRecents().dataOrEmpty()

    val uiState: StateFlow<SearchUiState> = combine(
        _query, storedBookmarks, storedRecents, problem, settings.settings,
    ) { query, bookmarkList, recentList, currentProblem, _ ->
        val isBrowsing = mode == SearchMode.Browse
        // Pas de suggestions pour « ajouter un favori » : l'utilisateur tape l'adresse qu'il veut garder.
        val suggestions = if (isBrowsing) SuggestionBuilder.build(query, bookmarkList, recentList) else emptyList()
        SearchUiState(
            mode = mode,
            engineName = settings.current.searchEngine.displayName,
            suggestions = suggestions,
            canClearRecents = isBrowsing && query.isBlank() && recentList.isNotEmpty(),
            problem = currentProblem,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        SearchUiState(mode, settings.current.searchEngine.displayName, emptyList(), false, null),
    )

    fun onQueryChange(text: String) {
        _query.value = text
        problem.value = null
    }

    /** Validation du clavier (Go / Rechercher) ou de la saisie courante. */
    fun onSubmit() {
        val resolved = InputResolver.resolve(_query.value, settings.current.searchEngine) ?: return
        when (mode) {
            SearchMode.Browse -> open(resolved)
            SearchMode.AddBookmark -> addBookmark(resolved)
        }
    }

    fun onSuggestionSelected(suggestion: Suggestion) {
        when (suggestion) {
            is Suggestion.Favorite -> send(SearchEvent.Open(suggestion.url))
            is Suggestion.Recent -> open(search(suggestion.query))
        }
    }

    fun onClearRecents() {
        viewModelScope.launch { recents.clear() }
    }

    private fun search(query: String): ResolvedInput.Search =
        ResolvedInput.Search(query, settings.current.searchEngine.searchUrl(query))

    private fun open(resolved: ResolvedInput) {
        viewModelScope.launch {
            // Mémorisation facultative : un échec de stockage n'empêche jamais la recherche.
            if (resolved is ResolvedInput.Search) recents.record(resolved.query)
            send(SearchEvent.Open(resolved.url))
        }
    }

    private fun addBookmark(resolved: ResolvedInput) {
        val isAddress = resolved is ResolvedInput.Url
        if (!isAddress) {
            problem.value = SearchProblem.NotAnAddress
            return
        }
        viewModelScope.launch {
            when (bookmarks.add(resolved.url, title = BookmarkUrl.siteName(resolved.url))) {
                is Resource.Error -> problem.value = SearchProblem.SaveFailed
                else -> send(SearchEvent.Close)
            }
        }
    }

    private fun send(event: SearchEvent) {
        _events.trySend(event)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun <T> Flow<Resource<List<T>>>.dataOrEmpty(): Flow<List<T>> =
    map { (it as? Resource.Success)?.data.orEmpty() }

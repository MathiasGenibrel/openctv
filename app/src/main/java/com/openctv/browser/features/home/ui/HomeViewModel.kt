package com.openctv.browser.features.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.navigation.Destination
import com.openctv.browser.features.bookmarks.domain.BookmarksContract
import com.openctv.browser.features.home.domain.BackPressOutcome
import com.openctv.browser.features.home.domain.DoubleBackPress
import com.openctv.browser.features.home.domain.HomeFocus
import com.openctv.browser.features.home.domain.HomeFavorite
import com.openctv.browser.features.home.domain.toHomeFavorite
import com.openctv.browser.features.search.domain.SearchMode
import com.openctv.browser.features.search.domain.SearchRequest
import com.openctv.browser.features.settings.domain.SettingsContract
import com.openctv.browser.features.settings.domain.orDefaults
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Effet ponctuel adressé à la vue. */
sealed interface HomeEvent {
    data class Navigate(val destination: Destination) : HomeEvent
    data object Exit : HomeEvent
}

/**
 * Accueil, racine de la navigation : vit avec l'activité (pas avec la destination) pour se souvenir de
 * l'élément focalisé quand l'utilisateur y revient.
 */
class HomeViewModel(
    private val bookmarks: BookmarksContract,
    settings: SettingsContract,
    private val doubleBackPress: DoubleBackPress = DoubleBackPress(),
) : ViewModel() {

    /** Favoris à afficher ; liste vide = état vide. */
    val favorites: StateFlow<Resource<List<HomeFavorite>>> = bookmarks.getBookmarks()
        .map { resource ->
            when (resource) {
                Resource.Loading -> Resource.Loading
                is Resource.Error -> resource
                is Resource.Success -> Resource.Success(resource.data.mapIndexed { i, b -> b.toHomeFavorite(i) })
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), Resource.Loading)

    /** Astuce « Maintenir : options du favori » : seulement s'il existe au moins un favori à maintenir. */
    val isFavoriteOptionsHintVisible: StateFlow<Boolean> = favorites
        .map { resource ->
            val shown = (resource as? Resource.Success)?.data.orEmpty()
            shown.isNotEmpty()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** Nom du moteur de recherche choisi dans les réglages, affiché dans la barre de recherche. */
    val searchEngineName: StateFlow<String> = settings.settings
        .map { it.orDefaults().searchEngine.displayName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), settings.current.searchEngine.displayName)

    private val _isExitHintVisible = MutableStateFlow(false)

    /** Avertissement « Appuyez encore sur Retour pour quitter » : visible tant qu'un second appui quitterait. */
    val isExitHintVisible: StateFlow<Boolean> = _isExitHintVisible.asStateFlow()
    private var hideExitHint: Job? = null

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    private val _searchInput = MutableStateFlow<SearchRequest?>(null)

    /** Saisie ouverte en surcouche de l'accueil (`null` = fermée) ; l'accueil reste vivant dessous. */
    val searchInput: StateFlow<SearchRequest?> = _searchInput.asStateFlow()

    private val _favoriteMenu = MutableStateFlow<HomeFavorite?>(null)

    /** Favori dont le petit menu « Ouvrir / Retirer » est ouvert (`null` = fermé) ; l'accueil reste vivant dessous. */
    val favoriteMenu: StateFlow<HomeFavorite?> = _favoriteMenu.asStateFlow()

    private var lastFocused: HomeFocus? = null

    /**
     * Vrai entre la fermeture du petit menu d'un favori et la restitution du focus : le menu qui quitte la
     * composition libère le focus, que le moteur donne alors à la première cible (la recherche). Ce focus
     * de repli ne doit pas écraser l'élément à restituer.
     */
    private var isRestoringFocus = false

    /** L'utilisateur a placé le focus sur [focus] : c'est là qu'il reviendra. */
    fun onFocused(focus: HomeFocus) {
        if (isRestoringFocus) return
        lastFocused = focus
    }

    /** Élément à focaliser quand l'accueil (ré)apparaît ou que la saisie se ferme. */
    fun focusToRestore(): HomeFocus = HomeFocus.restore(lastFocused, favorites.value)

    /** L'accueil a rendu le focus à [focusToRestore] : les changements de focus sont de nouveau mémorisés. */
    fun onFocusRestored() {
        isRestoringFocus = false
    }

    fun onOpenFavorite(favorite: HomeFavorite) = navigate(Destination.Browser(favorite.url))

    /** Appui long sur OK (ou Menu) sur une tuile : ouvre ses actions ; Retour ([onFavoriteMenuClosed]) les annule. */
    fun onFavoriteMenuRequested(favorite: HomeFavorite) {
        lastFocused = HomeFocus.Favorite(favorite.url)
        _favoriteMenu.value = favorite
    }

    fun onFavoriteMenuClosed() {
        isRestoringFocus = true
        _favoriteMenu.value = null
    }

    /** « Ouvrir » du petit menu. */
    fun onOpenFromMenu() {
        val favorite = _favoriteMenu.value ?: return
        _favoriteMenu.value = null
        onOpenFavorite(favorite)
    }

    /**
     * « Retirer » du petit menu : le focus passe à la tuile voisine (suivante, sinon précédente, sinon la tuile
     * « Ajouter ») avant même la mise à jour de la grille, pour ne jamais rester sans élément focalisé.
     */
    fun onRemoveFromMenu() {
        val favorite = _favoriteMenu.value ?: return
        lastFocused = neighbourFocusOf(favorite)
        isRestoringFocus = true
        _favoriteMenu.value = null
        viewModelScope.launch { bookmarks.remove(favorite.url) }
    }

    private fun neighbourFocusOf(removed: HomeFavorite): HomeFocus {
        val shown = (favorites.value as? Resource.Success)?.data.orEmpty()
        val index = shown.indexOfFirst { it.url == removed.url }
        val neighbour = shown.getOrNull(index + 1) ?: shown.getOrNull(index - 1)
        return neighbour?.let { HomeFocus.Favorite(it.url) } ?: HomeFocus.Add
    }

    fun onOpenSettings() = navigate(Destination.Settings)

    fun onOpenHistory() = navigate(Destination.History)

    fun onOpenSearch() = openSearch(SearchMode.Browse)

    fun onAddBookmark() = openSearch(SearchMode.AddBookmark)

    /** Saisie fermée sans rien ouvrir (Retour, ou favori ajouté) : l'accueil rend le focus. */
    fun onSearchClosed() {
        _searchInput.value = null
    }

    /** Adresse ou recherche validée depuis l'accueil : ouverte dans le navigateur. */
    fun onAddressChosen(url: String) {
        _searchInput.value = null
        navigate(Destination.Browser(url))
    }

    fun onBack() {
        when (doubleBackPress.onBackPressed()) {
            BackPressOutcome.ShowExitHint -> showExitHint()
            BackPressOutcome.Exit -> _events.trySend(HomeEvent.Exit)
        }
    }

    private fun openSearch(mode: SearchMode) {
        leave()
        _searchInput.value = SearchRequest(mode)
    }

    private fun navigate(destination: Destination) {
        leave()
        _events.trySend(HomeEvent.Navigate(destination))
    }

    /** L'accueil cède la main : un Retour à venir recommence le double appui (jamais de sortie par surprise). */
    private fun leave() {
        doubleBackPress.reset()
        hideExitHint?.cancel()
        _isExitHintVisible.value = false
    }

    private fun showExitHint() {
        _isExitHintVisible.value = true
        hideExitHint?.cancel()
        hideExitHint = viewModelScope.launch {
            delay(doubleBackPress.window.toMillis())
            _isExitHintVisible.value = false
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

package com.openctv.browser.features.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.navigation.Destination
import com.openctv.browser.features.bookmarks.domain.BookmarksContract
import com.openctv.browser.features.home.domain.BackPressOutcome
import com.openctv.browser.features.home.domain.DoubleBackPress
import com.openctv.browser.features.home.domain.HomeFavorite
import com.openctv.browser.features.home.domain.toHomeFavorite
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

class HomeViewModel(
    bookmarks: BookmarksContract,
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

    private val _isExitHintVisible = MutableStateFlow(false)

    /** Avertissement « Appuyez encore sur Retour pour quitter » : visible tant qu'un second appui quitterait. */
    val isExitHintVisible: StateFlow<Boolean> = _isExitHintVisible.asStateFlow()
    private var hideExitHint: Job? = null

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    fun onOpenFavorite(favorite: HomeFavorite) {
        _events.trySend(HomeEvent.Navigate(Destination.Browser(favorite.url)))
    }

    fun onBack() {
        when (doubleBackPress.onBackPressed()) {
            BackPressOutcome.ShowExitHint -> showExitHint()
            BackPressOutcome.Exit -> _events.trySend(HomeEvent.Exit)
        }
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

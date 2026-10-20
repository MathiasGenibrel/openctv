package com.openctv.browser.features.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openctv.browser.core.common.Resource
import com.openctv.browser.features.bookmarks.domain.Bookmark
import com.openctv.browser.features.bookmarks.domain.BookmarksContract
import com.openctv.browser.features.settings.domain.CursorSpeed
import com.openctv.browser.features.settings.domain.HudHideDelay
import com.openctv.browser.features.settings.domain.SearchEngine
import com.openctv.browser.features.settings.domain.Settings
import com.openctv.browser.features.settings.domain.SettingsContract
import com.openctv.browser.features.settings.domain.StartPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Sous-panneau latéral ouvert depuis la liste des réglages. */
enum class SettingsPanel { StartPage, SearchEngine, HudHideDelay, CursorSpeed }

data class SettingsUiState(
    val settings: Settings = Settings(),
    /** Favoris proposés comme sites de démarrage. */
    val bookmarks: List<Bookmark> = emptyList(),
    val openPanel: SettingsPanel? = null,
    /** Le dernier enregistrement a échoué : le réglage affiché est resté l'ancien. */
    val isSaveFailed: Boolean = false,
    /** La dernière adresse saisie comme page de démarrage n'était pas utilisable. */
    val isSiteInputInvalid: Boolean = false,
)

class SettingsViewModel(
    private val settings: SettingsContract,
    bookmarks: BookmarksContract,
) : ViewModel() {

    private val openPanel = MutableStateFlow<SettingsPanel?>(null)
    private val feedback = MutableStateFlow(Feedback())

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.settings,
        bookmarks.getBookmarks(),
        openPanel,
        feedback,
    ) { storedSettings, storedBookmarks, panel, feedback ->
        SettingsUiState(
            settings = (storedSettings as? Resource.Success)?.data ?: Settings(),
            bookmarks = (storedBookmarks as? Resource.Success)?.data.orEmpty(),
            openPanel = panel,
            isSaveFailed = feedback.isSaveFailed,
            isSiteInputInvalid = feedback.isSiteInputInvalid,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), SettingsUiState())

    fun open(panel: SettingsPanel) {
        feedback.value = Feedback()
        openPanel.value = panel
    }

    fun closePanel() {
        openPanel.value = null
    }

    /**
     * Retour : ferme le sous-panneau ouvert. Renvoie `false` quand il n'y en a pas,
     * l'écran revient alors à l'accueil.
     */
    fun onBack(): Boolean {
        val isPanelOpen = openPanel.value != null
        if (isPanelOpen) closePanel()
        return isPanelOpen
    }

    fun selectSearchEngine(engine: SearchEngine) = save { settings.setSearchEngine(engine) }

    fun selectStartPage(page: StartPage) = save { settings.setStartPage(page) }

    /** Page de démarrage saisie à la main : refusée (sans rien changer) si ce n'est pas une adresse web. */
    fun selectStartSite(rawInput: String) {
        val site = StartPage.fromInput(rawInput)
        feedback.update { it.copy(isSiteInputInvalid = site == null) }
        if (site != null) selectStartPage(site)
    }

    fun selectHudHideDelay(delay: HudHideDelay) = save { settings.setHudHideDelay(delay) }

    fun selectCursorSpeed(speed: CursorSpeed) = save { settings.setCursorSpeed(speed) }

    fun setAdBlockEnabled(isEnabled: Boolean) = save { settings.setAdBlockEnabled(isEnabled) }

    private fun save(write: suspend () -> Resource<Unit>) {
        viewModelScope.launch {
            val isFailure = write() is Resource.Error
            feedback.update { it.copy(isSaveFailed = isFailure) }
        }
    }

    private data class Feedback(val isSaveFailed: Boolean = false, val isSiteInputInvalid: Boolean = false)

    private companion object {
        const val StopTimeoutMillis = 5_000L
    }
}

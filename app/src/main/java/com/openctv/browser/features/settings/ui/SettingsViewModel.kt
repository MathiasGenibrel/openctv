package com.openctv.browser.features.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.security.NoSecurityExceptions
import com.openctv.browser.core.security.SecurityExceptionsContract
import com.openctv.browser.core.security.SiteException
import com.openctv.browser.core.zoom.NoPageZoom
import com.openctv.browser.core.zoom.PageZoom
import com.openctv.browser.core.zoom.PageZoomContract
import com.openctv.browser.core.zoom.SiteZoom
import com.openctv.browser.features.bookmarks.domain.Bookmark
import com.openctv.browser.features.bookmarks.domain.BookmarksContract
import com.openctv.browser.features.history.domain.HistoryContract
import com.openctv.browser.features.search.domain.RecentSearchesContract
import com.openctv.browser.features.settings.domain.ClearBrowsingData
import com.openctv.browser.features.settings.domain.ClearDataSelection
import com.openctv.browser.features.settings.domain.ClearDataStep
import com.openctv.browser.features.settings.domain.CursorSpeed
import com.openctv.browser.features.settings.domain.HudHideDelay
import com.openctv.browser.features.settings.domain.SearchEngine
import com.openctv.browser.features.settings.domain.Settings
import com.openctv.browser.features.settings.domain.SettingsContract
import com.openctv.browser.features.settings.domain.StartPage
import com.openctv.browser.features.settings.domain.orDefaults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Sous-panneau latéral ouvert depuis la liste des réglages. */
enum class SettingsPanel { StartPage, SearchEngine, HudHideDelay, CursorSpeed, PageZoom, SecurityExceptions, ClearData }

data class SettingsUiState(
    val settings: Settings = Settings(),
    /** Favoris proposés comme sites de démarrage. */
    val bookmarks: List<Bookmark> = emptyList(),
    /** Exceptions de sécurité mémorisées (hôtes HTTP autorisés, certificats de confiance), triées par hôte. */
    val securityExceptions: List<SiteException> = emptyList(),
    /** Sites qui ont leur propre zoom (les autres suivent `settings.pageZoom`), triés par hôte. */
    val siteZooms: List<SiteZoom> = emptyList(),
    /** Catégories cochées dans le panneau « Effacer les données » (toutes à chaque ouverture). */
    val clearSelection: ClearDataSelection = ClearDataSelection(),
    /** Pages de l'historique, `null` tant que (ou si) il n'est pas lisible : le compteur est alors omis. */
    val historyCount: Int? = null,
    /** Recherches récentes, `null` tant que (ou si) elles ne sont pas lisibles. */
    val recentSearchCount: Int? = null,
    /** Résultat du dernier effacement, affiché en snackbar jusqu'à [SettingsViewModel.onClearOutcomeShown]. */
    val clearOutcome: ClearOutcome? = null,
    val openPanel: SettingsPanel? = null,
    /** Le dernier enregistrement a échoué : le réglage affiché est resté l'ancien. */
    val isSaveFailed: Boolean = false,
    /** La dernière adresse saisie comme page de démarrage n'était pas utilisable. */
    val isSiteInputInvalid: Boolean = false,
)

/** Ce que dit la snackbar après « Effacer les données ». */
sealed interface ClearOutcome {
    data object Done : ClearOutcome

    /** Ces catégories n'ont pas pu être effacées ; les autres l'ont été. */
    data class Failed(val steps: Set<ClearDataStep>) : ClearOutcome
}

class SettingsViewModel(
    private val settings: SettingsContract,
    bookmarks: BookmarksContract,
    private val securityExceptions: SecurityExceptionsContract = NoSecurityExceptions,
    private val siteZooms: PageZoomContract = NoPageZoom,
    private val clearBrowsingData: ClearBrowsingData,
    history: HistoryContract,
    recentSearches: RecentSearchesContract,
) : ViewModel() {

    private val openPanel = MutableStateFlow<SettingsPanel?>(null)
    private val feedback = MutableStateFlow(Feedback())
    private val clearing = MutableStateFlow(Clearing())

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(settings.settings, bookmarks.getBookmarks(), securityExceptions.persisted, siteZooms.sites, ::StoredData),
        openPanel,
        feedback,
        clearing,
        combine(history.getEntries(), recentSearches.getRecents(), ::Counts),
    ) { stored, panel, feedback, clearing, counts ->
        SettingsUiState(
            settings = stored.settings.orDefaults(),
            bookmarks = (stored.bookmarks as? Resource.Success)?.data.orEmpty(),
            securityExceptions = (stored.securityExceptions as? Resource.Success)?.data.orEmpty(),
            siteZooms = (stored.siteZooms as? Resource.Success)?.data.orEmpty(),
            clearSelection = clearing.selection,
            historyCount = counts.history,
            recentSearchCount = counts.recentSearches,
            clearOutcome = clearing.outcome,
            openPanel = panel,
            isSaveFailed = feedback.isSaveFailed,
            isSiteInputInvalid = feedback.isSiteInputInvalid,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), SettingsUiState())

    fun open(panel: SettingsPanel) {
        feedback.value = Feedback()
        // Chaque ouverture repart de « tout coché » : on n'efface jamais sur la foi d'un choix d'une visite précédente.
        if (panel == SettingsPanel.ClearData) clearing.update { it.copy(selection = ClearDataSelection()) }
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

    /** Niveau de zoom des sites qui n'ont pas le leur. */
    fun selectPageZoom(zoom: PageZoom) = save { settings.setPageZoom(zoom) }

    /** Retire le zoom propre d'un site : il revient au niveau par défaut. */
    fun removeSiteZoom(host: String) = save { siteZooms.remove(host) }

    fun setAdBlockEnabled(isEnabled: Boolean) = save { settings.setAdBlockEnabled(isEnabled) }

    fun setCookieBannerBlockEnabled(isEnabled: Boolean) = save { settings.setCookieBannerBlockEnabled(isEnabled) }

    /** Retire une exception de sécurité : la prochaine visite du site affiche de nouveau l'avertissement. */
    fun removeSecurityException(exception: SiteException) = save { securityExceptions.remove(exception) }

    fun toggleClearStep(step: ClearDataStep) = clearing.update { it.copy(selection = it.selection.toggled(step)) }

    /**
     * Efface les catégories cochées, puis ferme le panneau (le focus revient à la ligne des Réglages) et publie le
     * résultat. Sans rien de coché, ou pendant un effacement déjà lancé, ne fait rien.
     */
    fun clearData() {
        val selection = clearing.value.selection
        val isBusy = clearing.value.isClearing
        if (selection.isEmpty || isBusy) return
        clearing.update { it.copy(isClearing = true, outcome = null) }
        viewModelScope.launch {
            val result = clearBrowsingData(selection)
            val outcome = if (result.isSuccess) ClearOutcome.Done else ClearOutcome.Failed(result.failed)
            clearing.update { it.copy(isClearing = false, outcome = outcome) }
            closePanel()
        }
    }

    /** La snackbar de résultat a été montrée assez longtemps. */
    fun onClearOutcomeShown() = clearing.update { it.copy(outcome = null) }

    private fun save(write: suspend () -> Resource<Unit>) {
        viewModelScope.launch {
            val isFailure = write() is Resource.Error
            feedback.update { it.copy(isSaveFailed = isFailure) }
        }
    }

    private class StoredData(
        val settings: Resource<Settings>,
        val bookmarks: Resource<List<Bookmark>>,
        val securityExceptions: Resource<List<SiteException>>,
        val siteZooms: Resource<List<SiteZoom>>,
    )

    private class Counts(entries: Resource<List<*>>, recents: Resource<List<*>>) {
        val history: Int? = (entries as? Resource.Success)?.data?.size
        val recentSearches: Int? = (recents as? Resource.Success)?.data?.size
    }

    private data class Clearing(
        val selection: ClearDataSelection = ClearDataSelection(),
        val isClearing: Boolean = false,
        val outcome: ClearOutcome? = null,
    )

    private data class Feedback(val isSaveFailed: Boolean = false, val isSiteInputInvalid: Boolean = false)

    private companion object {
        const val StopTimeoutMillis = 5_000L
    }
}

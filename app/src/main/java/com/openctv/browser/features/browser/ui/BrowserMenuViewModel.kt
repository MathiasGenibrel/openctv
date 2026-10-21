package com.openctv.browser.features.browser.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.security.SiteHost
import com.openctv.browser.core.zoom.PageZoom
import com.openctv.browser.features.adblock.domain.AdBlockContract
import com.openctv.browser.features.bookmarks.domain.BookmarksContract
import com.openctv.browser.features.browser.domain.AdBlockStatus
import com.openctv.browser.features.browser.domain.BrowserMenuPanel
import com.openctv.browser.features.browser.domain.BrowserMenuState
import com.openctv.browser.features.browser.domain.MenuWriteFailure
import com.openctv.browser.features.browser.domain.adBlockStatusOf
import com.openctv.browser.features.browser.domain.siteLabelOf
import com.openctv.browser.features.settings.domain.CursorSpeed
import com.openctv.browser.features.settings.domain.SearchEngine
import com.openctv.browser.features.settings.domain.SettingsContract
import com.openctv.browser.features.settings.domain.orDefaults
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Menu du navigateur pour la page [pageUrl] : bloqueur du site, favori, zoom de la page, moteur de recherche et vitesse
 * du curseur. Actualiser et Accueil sont des ordres du navigateur (`BrowserViewModel`), pas d'ici.
 * Un échec d'écriture est remonté par [onWriteFailed] ; l'affichage reste sur la valeur réellement stockée.
 */
class BrowserMenuViewModel(
    private val pageUrl: StateFlow<String>,
    private val settings: SettingsContract,
    private val adBlock: AdBlockContract,
    private val bookmarks: BookmarksContract,
    private val onWriteFailed: (MenuWriteFailure) -> Unit,
    /** Zoom appliqué à la page (porté par le navigateur, qui le mémorise et l'applique à la WebView). */
    private val pageZoom: StateFlow<PageZoom> = MutableStateFlow(PageZoom.Default),
    /** Niveau choisi pour le site, et niveau par défaut des Réglages au moment du choix. */
    private val onZoomChosen: (zoom: PageZoom, default: PageZoom) -> Unit = { _, _ -> },
) : ViewModel() {

    private val openPanel = MutableStateFlow<BrowserMenuPanel?>(null)

    /** Réglages concernant la page courante, recalculés quand elle change (page monopage, redirection). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val pageSettings: Flow<PageSettings> = pageUrl.flatMapLatest { url ->
        combine(adBlock.isEnabledForSite(url), adBlock.blockedCount(url), bookmarks.isBookmarked(url)) {
                isEnabledForSite, blockedCount, bookmarked ->
            PageSettings(url, isEnabledForSite, blockedCount, isBookmarked = bookmarked is Resource.Success && bookmarked.data)
        }
    }

    val state: StateFlow<BrowserMenuState> = combine(
        pageSettings, settings.settings, openPanel, pageZoom,
    ) { page, stored, panel, zoom ->
        val current = stored.orDefaults()
        BrowserMenuState(
            site = siteLabelOf(page.url),
            adBlock = adBlockStatusOf(current.isAdBlockEnabled, page.isEnabledForSite, page.blockedCount),
            isBookmarked = page.isBookmarked,
            pageZoom = zoom,
            defaultZoom = current.pageZoom,
            zoomHost = SiteHost.of(page.url),
            searchEngine = current.searchEngine,
            cursorSpeed = current.cursorSpeed,
            openPanel = panel,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        BrowserMenuState(
            site = siteLabelOf(pageUrl.value),
            pageZoom = pageZoom.value,
            defaultZoom = settings.current.pageZoom,
            zoomHost = SiteHost.of(pageUrl.value),
        ),
    )

    /**
     * Interrupteur du bloqueur pour ce site. Sans effet quand le bloqueur est coupé partout : l'interrupteur
     * du site ne changerait rien à ce que voit l'utilisateur (la ligne l'explique).
     */
    fun toggleAdBlock() {
        val isGloballyDisabled = state.value.adBlock == AdBlockStatus.DisabledEverywhere
        if (isGloballyDisabled) return
        val shouldEnable = !state.value.isAdBlockActive
        val url = pageUrl.value
        viewModelScope.launch {
            if (adBlock.setEnabledForSite(url, shouldEnable) is Resource.Error) onWriteFailed(MenuWriteFailure.AdBlock)
        }
    }

    fun toggleBookmark() {
        val url = pageUrl.value
        val isAlreadyBookmarked = state.value.isBookmarked
        viewModelScope.launch {
            val result = if (isAlreadyBookmarked) bookmarks.remove(url) else bookmarks.add(url, siteLabelOf(url))
            if (result is Resource.Error) onWriteFailed(MenuWriteFailure.Bookmark)
        }
    }

    fun openPanel(panel: BrowserMenuPanel) {
        openPanel.value = panel
    }

    fun closePanel() {
        openPanel.update { null }
    }

    /** Retour : ferme le sous-panneau ouvert. Renvoie `false` s'il n'y en a pas (le menu lui-même se ferme alors). */
    fun onBack(): Boolean {
        val isPanelOpen = openPanel.value != null
        if (isPanelOpen) closePanel()
        return isPanelOpen
    }

    /** Niveau choisi dans « Zoom de la page » : le navigateur l'applique, le mémorise (ou retire le zoom du site si c'est le niveau par défaut). */
    fun selectPageZoom(zoom: PageZoom) = onZoomChosen(zoom, state.value.defaultZoom)

    fun selectSearchEngine(engine: SearchEngine) = save { settings.setSearchEngine(engine) }

    fun selectCursorSpeed(speed: CursorSpeed) = save { settings.setCursorSpeed(speed) }

    private fun save(write: suspend () -> Resource<Unit>) {
        viewModelScope.launch {
            if (write() is Resource.Error) onWriteFailed(MenuWriteFailure.Setting)
        }
    }

    private data class PageSettings(
        val url: String,
        val isEnabledForSite: Boolean,
        val blockedCount: Int,
        val isBookmarked: Boolean,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

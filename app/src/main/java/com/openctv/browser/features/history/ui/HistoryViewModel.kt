package com.openctv.browser.features.history.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openctv.browser.core.common.Resource
import com.openctv.browser.features.history.domain.HistoryContract
import com.openctv.browser.features.history.domain.HistoryDay
import com.openctv.browser.features.history.domain.HistoryEntry
import com.openctv.browser.features.history.domain.HistoryFocus
import com.openctv.browser.features.history.domain.groupByDay
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Ce que montre la liste de l'écran Historique. */
sealed interface HistoryContent {
    data object Loading : HistoryContent

    /** Le stockage n'a pas pu être lu : l'écran le dit, le bouton retour reste utilisable. */
    data object Unavailable : HistoryContent

    /** Aucune page visitée. */
    data object Empty : HistoryContent

    data class Entries(val days: List<HistoryDay>) : HistoryContent {
        val entries: List<HistoryEntry> get() = days.flatMap { it.entries }
        val count: Int get() = days.sumOf { it.entries.size }
    }
}

data class HistoryUiState(
    val content: HistoryContent = HistoryContent.Loading,
    /** Dialogue « Effacer tout l'historique ? » ouvert. */
    val isClearConfirmationOpen: Boolean = false,
    /** Un retrait ou l'effacement a échoué : l'écran le dit. */
    val isWriteFailed: Boolean = false,
    /** Élément focalisé quand la liste est lue (cf. [HistoryFocus.initial]). */
    val initialFocus: HistoryFocus = HistoryFocus.BackButton,
)

/**
 * Écran Historique. Le ViewModel décide (contenu, dialogue, focus après retrait), la vue exécute.
 * Ouvrir une page est une décision de l'écran d'origine (accueil ou navigateur), pas d'ici.
 * [focusedEntryId] : entrée à refocaliser à l'ouverture (retour de la page qu'elle avait ouverte).
 */
class HistoryViewModel(
    private val history: HistoryContract,
    private val focusedEntryId: Long? = null,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {

    private val entries: StateFlow<Resource<List<HistoryEntry>>> = history.getEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), Resource.Loading)

    private val isClearConfirmationOpen = MutableStateFlow(false)
    private val isWriteFailed = MutableStateFlow(false)
    private val _focus = MutableStateFlow<HistoryFocus?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(entries, isClearConfirmationOpen, isWriteFailed) { resource, isOpen, isFailed ->
        val entries = (resource as? Resource.Success)?.data.orEmpty()
        HistoryUiState(contentOf(resource), isOpen, isFailed, HistoryFocus.initial(entries, focusedEntryId))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HistoryUiState())

    /** Focus demandé par une action (retrait, dialogue fermé) ; la vue le prend puis appelle [onFocusHandled]. */
    val focus: StateFlow<HistoryFocus?> = _focus.asStateFlow()

    fun onFocusHandled() {
        _focus.value = null
    }

    /** Retire l'entrée ; le focus passe à sa voisine (ou, sans plus d'entrée, au bouton retour). */
    fun remove(entry: HistoryEntry) {
        val shown = (uiState.value.content as? HistoryContent.Entries)?.entries.orEmpty()
        val next = HistoryFocus.afterRemoving(shown, entry.id)
        viewModelScope.launch {
            when (history.remove(entry.id)) {
                is Resource.Error -> isWriteFailed.value = true
                else -> {
                    isWriteFailed.value = false
                    _focus.value = next
                }
            }
        }
    }

    fun requestClear() {
        val hasEntries = uiState.value.content is HistoryContent.Entries
        if (hasEntries) isClearConfirmationOpen.value = true
    }

    /** « Annuler » ou Retour : le dialogue se ferme, le focus revient à « Tout effacer ». */
    fun cancelClear() {
        isClearConfirmationOpen.value = false
        _focus.value = HistoryFocus.ClearAll
    }

    /** « Effacer » : tout l'historique part ; il ne reste que le bouton retour à focaliser. */
    fun confirmClear() {
        isClearConfirmationOpen.value = false
        viewModelScope.launch {
            val result = history.clear()
            val isFailed = result is Resource.Error
            isWriteFailed.value = isFailed
            _focus.value = if (isFailed) HistoryFocus.ClearAll else HistoryFocus.BackButton
        }
    }

    /** Retour ferme le dialogue s'il est ouvert. Renvoie `false` sinon : l'écran se ferme alors. */
    fun onBack(): Boolean {
        val isDialogOpen = isClearConfirmationOpen.value
        if (isDialogOpen) cancelClear()
        return isDialogOpen
    }

    private fun contentOf(resource: Resource<List<HistoryEntry>>): HistoryContent = when (resource) {
        Resource.Loading -> HistoryContent.Loading
        is Resource.Error -> HistoryContent.Unavailable
        is Resource.Success ->
            if (resource.data.isEmpty()) HistoryContent.Empty
            else HistoryContent.Entries(groupByDay(resource.data, today(), zone()))
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

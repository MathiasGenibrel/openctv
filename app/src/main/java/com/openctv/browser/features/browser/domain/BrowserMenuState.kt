package com.openctv.browser.features.browser.domain

import com.openctv.browser.core.zoom.PageZoom
import com.openctv.browser.features.settings.domain.CursorSpeed
import com.openctv.browser.features.settings.domain.SearchEngine

/** Écriture du menu qui a échoué ; le navigateur décide du message affiché (cf. `BrowserViewModel.onMenuWriteFailed`). */
enum class MenuWriteFailure { AdBlock, Bookmark, Setting }

/** Sous-panneau du menu (radios), ouvert depuis « Zoom de la page », « Moteur de recherche » ou « Vitesse du curseur ». */
enum class BrowserMenuPanel { SearchEngine, CursorSpeed, PageZoom }

/** Ce que dit la ligne « Bloqueur de pubs » du menu. */
sealed interface AdBlockStatus {
    /** Bloqueur actif sur ce site, [blockedCount] requêtes bloquées depuis le lancement. */
    data class Active(val blockedCount: Int) : AdBlockStatus

    /** Désactivé pour ce site seulement. */
    data object DisabledForSite : AdBlockStatus

    /** Interrupteur global des Réglages coupé : l'interrupteur du site ne change rien. */
    data object DisabledEverywhere : AdBlockStatus
}

/** État affiché par le menu du navigateur pour la page courante. */
data class BrowserMenuState(
    /** Domaine de la page (`arte.tv`). */
    val site: String = "",
    val adBlock: AdBlockStatus = AdBlockStatus.Active(blockedCount = 0),
    val isBookmarked: Boolean = false,
    /** Zoom appliqué à la page (celui de son site, sinon le niveau par défaut). */
    val pageZoom: PageZoom = PageZoom.Default,
    /** Niveau par défaut des Réglages : choisi pour un site, il retire son zoom propre. */
    val defaultZoom: PageZoom = PageZoom.Default,
    /** Hôte exact auquel se rattache le zoom mémorisé (`SiteHost`) ; `null` si l'adresse n'a pas d'hôte lisible. */
    val zoomHost: String? = null,
    val searchEngine: SearchEngine = SearchEngine.Default,
    val cursorSpeed: CursorSpeed = CursorSpeed.Default,
    val openPanel: BrowserMenuPanel? = null,
) {
    val isAdBlockActive: Boolean get() = adBlock is AdBlockStatus.Active
}

/** Statut du bloqueur : l'interrupteur global prime sur celui du site. */
fun adBlockStatusOf(isEnabledGlobally: Boolean, isEnabledForSite: Boolean, blockedCount: Int): AdBlockStatus = when {
    !isEnabledGlobally -> AdBlockStatus.DisabledEverywhere
    !isEnabledForSite -> AdBlockStatus.DisabledForSite
    else -> AdBlockStatus.Active(blockedCount)
}

package com.openctv.browser.features.browser.domain

import com.openctv.browser.features.settings.domain.CursorSpeed
import com.openctv.browser.features.settings.domain.SearchEngine

/** Sous-panneau du menu (radios), ouvert depuis « Moteur de recherche » ou « Vitesse du curseur ». */
enum class BrowserMenuPanel { SearchEngine, CursorSpeed }

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

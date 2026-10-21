package com.openctv.browser.core.navmode

import com.openctv.browser.core.common.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Mode de navigation propre à chaque site, mémorisé sur la TV uniquement. Seuls les sites passés en mode éléments
 * sont stockés : tout autre site est au curseur ([navigationModeOf]).
 */
interface NavigationModeContract {
    /** Hôtes exacts (`SiteHost`) en mode éléments ; `Loading` jusqu'à la première lecture. */
    val elementHosts: StateFlow<Resource<Set<String>>>

    /** Mémorise [mode] pour [host] (hôte normalisé par `SiteHost`) ; le mode curseur retire l'hôte de la liste. */
    suspend fun set(host: String, mode: NavigationMode): Resource<Unit>
}

/** Aucun site en mode éléments : valeur par défaut des composants qui n'ont pas besoin du stockage (tests, aperçus). */
object NoNavigationModes : NavigationModeContract {
    override val elementHosts: StateFlow<Resource<Set<String>>> = MutableStateFlow(Resource.Success(emptySet()))
    override suspend fun set(host: String, mode: NavigationMode): Resource<Unit> = Resource.Success(Unit)
}

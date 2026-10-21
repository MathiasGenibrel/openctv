package com.openctv.browser.core.zoom

import com.openctv.browser.core.common.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Zoom propre à certains sites, mémorisé sur la TV uniquement. Un site absent de la liste suit le niveau par
 * défaut des Réglages (`Settings.pageZoom`) : [effectiveZoom] combine les deux.
 */
interface PageZoomContract {
    /** Sites avec leur propre zoom, triés par hôte ; `Loading` jusqu'à la première lecture. */
    val sites: StateFlow<Resource<List<SiteZoom>>>

    /** Mémorise [zoom] pour [host] (hôte normalisé par `SiteHost`) ; la valeur affichée reste l'ancienne si l'écriture échoue. */
    suspend fun set(host: String, zoom: PageZoom): Resource<Unit>

    /** Retire le zoom propre de [host] : le site revient au niveau par défaut. */
    suspend fun remove(host: String): Resource<Unit>
}

/** Aucun site avec son propre zoom : valeur par défaut des composants qui n'ont pas besoin du stockage (tests, aperçus). */
object NoPageZoom : PageZoomContract {
    override val sites: StateFlow<Resource<List<SiteZoom>>> = MutableStateFlow(Resource.Success(emptyList()))
    override suspend fun set(host: String, zoom: PageZoom): Resource<Unit> = Resource.Success(Unit)
    override suspend fun remove(host: String): Resource<Unit> = Resource.Success(Unit)
}

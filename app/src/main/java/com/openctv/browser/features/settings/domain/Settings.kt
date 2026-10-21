package com.openctv.browser.features.settings.domain

import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.zoom.PageZoom

/** Préférences de l'utilisateur, stockées uniquement sur la TV. Les valeurs par défaut sont celles du premier lancement. */
data class Settings(
    val searchEngine: SearchEngine = SearchEngine.Default,
    val startPage: StartPage = StartPage.Default,
    val hudHideDelay: HudHideDelay = HudHideDelay.Default,
    val cursorSpeed: CursorSpeed = CursorSpeed.Default,
    /** Zoom des sites qui n'ont pas leur propre niveau (`PageZoomContract`). */
    val pageZoom: PageZoom = PageZoom.Default,
    /** Interrupteur global du bloqueur de pubs. */
    val isAdBlockEnabled: Boolean = true,
    /** Liste « EasyList Cookie » (requêtes bloquées + bandeaux masqués) ; sans effet si le bloqueur est coupé. */
    val isCookieBannerBlockEnabled: Boolean = true,
    /** Dernière page visitée (http/https), `null` si aucune. */
    val lastPage: String? = null,
) {
    /**
     * URL à ouvrir au lancement, ou `null` pour l'écran d'accueil.
     * « Reprendre la dernière page » sans historique retombe sur l'accueil.
     */
    fun launchUrl(): String? = when (startPage) {
        StartPage.Home -> null
        StartPage.LastPage -> lastPage
        is StartPage.Site -> startPage.url
    }
}

/**
 * Réglages à appliquer : la valeur lue, ou les défauts tant qu'elle ne l'est pas (lecture en cours,
 * stockage illisible). Un consommateur n'a ainsi jamais à traiter `Loading`/`Error` lui-même.
 */
fun Resource<Settings>.orDefaults(): Settings = (this as? Resource.Success)?.data ?: DefaultSettings

/** Instance partagée (immuable) : `orDefaults()` est lu pour chaque requête, sans allocation. */
private val DefaultSettings = Settings()

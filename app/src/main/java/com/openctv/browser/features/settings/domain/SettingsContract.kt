package com.openctv.browser.features.settings.domain

import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.zoom.PageZoom
import kotlinx.coroutines.flow.StateFlow

/**
 * Préférences d'OpenCTV, persistées localement (aucune donnée ne quitte la TV).
 *
 * Exposé dans `domain/` pour les autres features (saisie, curseur, HUD, menu, bloqueur) :
 * [settings] émet `Loading` jusqu'à la première lecture, puis `Success` à chaque changement.
 * Les écritures renvoient `Resource.Error` si le stockage échoue (la valeur courante reste inchangée).
 */
interface SettingsContract {
    val settings: StateFlow<Resource<Settings>>

    /**
     * Réglages courants (défauts tant que la lecture n'a pas abouti) : lecture synchrone, sans I/O ni
     * suspension, sûre depuis n'importe quel thread — utilisable dans `shouldInterceptRequest`.
     */
    val current: Settings get() = settings.value.orDefaults()

    /** URL à ouvrir au lancement, `null` = accueil (y compris si le stockage est illisible). Attend la lecture. */
    suspend fun launchUrl(): String?

    suspend fun setSearchEngine(engine: SearchEngine): Resource<Unit>
    suspend fun setStartPage(page: StartPage): Resource<Unit>
    suspend fun setHudHideDelay(delay: HudHideDelay): Resource<Unit>
    suspend fun setCursorSpeed(speed: CursorSpeed): Resource<Unit>
    suspend fun setPageZoom(zoom: PageZoom): Resource<Unit>
    suspend fun setAdBlockEnabled(isEnabled: Boolean): Resource<Unit>
    suspend fun setCookieBannerBlockEnabled(isEnabled: Boolean): Resource<Unit>

    /**
     * Enregistre la dernière page visitée (le navigateur l'appelle à chaque page chargée).
     * Sans attente ni résultat : n'est pas sur le chemin de navigation ; les URLs non http/https sont ignorées.
     */
    fun recordLastPage(url: String)

    /** Oublie la dernière page visitée : « Reprendre la dernière page » retombe alors sur l'accueil. */
    suspend fun clearLastPage(): Resource<Unit>
}

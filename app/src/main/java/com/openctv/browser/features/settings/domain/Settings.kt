package com.openctv.browser.features.settings.domain

/** Préférences de l'utilisateur, stockées uniquement sur la TV. Les valeurs par défaut sont celles du premier lancement. */
data class Settings(
    val searchEngine: SearchEngine = SearchEngine.Default,
    val startPage: StartPage = StartPage.Default,
    val hudHideDelay: HudHideDelay = HudHideDelay.Default,
    val cursorSpeed: CursorSpeed = CursorSpeed.Default,
    /** Interrupteur global du bloqueur de pubs. */
    val isAdBlockEnabled: Boolean = true,
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

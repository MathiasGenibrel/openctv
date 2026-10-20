package com.openctv.browser.features.adblock.domain

/**
 * Moteur de décision du bloqueur. Synchrone, sans I/O, thread-safe : appelable pour chaque
 * requête depuis `shouldInterceptRequest` (thread de fond).
 */
interface AdBlockEngine {

    /** `true` si la requête vers [requestUrl], émise par la page [pageUrl], doit être bloquée. */
    fun shouldBlock(requestUrl: String, pageUrl: String?): Boolean

    /** Remplace atomiquement les filtres (téléchargés et compilés hors du chemin de navigation). */
    fun updateFilters(filters: CompiledFilters)

    /** Active ou désactive le blocage pour un site (hôte ou URL) ; un site désactivé n'a rien de bloqué. */
    fun setEnabledForSite(siteHost: String, enabled: Boolean)

    fun isEnabledForSite(siteHost: String): Boolean
}

package com.openctv.browser.features.adblock.domain

import com.openctv.browser.core.webview.CosmeticSelection

/**
 * Moteur de décision du bloqueur. Synchrone, sans I/O, thread-safe : appelable pour chaque
 * requête depuis `shouldInterceptRequest` (thread de fond).
 *
 * Le moteur ne possède aucun état durable : il consomme des instantanés (filtres compilés, sites
 * désactivés) que la couche data lui pousse. La source de vérité de l'état « par site » est le
 * stockage persistant, qui appelle [updateDisabledSites] au démarrage puis à chaque changement.
 */
interface AdBlockEngine {

    /**
     * `true` si la requête vers [requestUrl], émise par la page [pageUrl], doit être bloquée. [withCookieList] : la
     * liste des bandeaux de cookies compte aussi (réglage « Bandeaux de cookies »), avec ses propres exceptions.
     *
     * [pageUrl] est l'URL de la page principale, mémorisée côté thread UI (`WebView.getUrl()`
     * n'est pas appelable depuis le thread de `shouldInterceptRequest`) ; `null` si inconnue.
     */
    fun shouldBlock(requestUrl: String, pageUrl: String?, withCookieList: Boolean = true): Boolean

    /**
     * Sélecteurs à masquer sur la page principale [pageUrl] (liste « EasyList Cookie »). Vide si la liste n'est pas
     * chargée ou si le blocage est désactivé pour ce site. Appelé une fois par page, hors du thread principal.
     */
    fun cosmeticSelectionFor(pageUrl: String): CosmeticSelection

    /** Remplace atomiquement les filtres (téléchargés et compilés hors du chemin de navigation). */
    fun updateFilters(filters: CompiledFilters)

    /** Remplace atomiquement la liste de bandeaux de cookies : règles réseau et règles de masquage ensemble. */
    fun updateCookieFilters(network: CompiledFilters, cosmetic: CosmeticFilters)

    /**
     * Remplace atomiquement l'ensemble des sites (hôtes ou URL) où le blocage est désactivé ;
     * un site désactivé, et ses sous-domaines, n'ont aucune requête bloquée.
     */
    fun updateDisabledSites(sites: Iterable<String>)

    fun isEnabledForSite(siteHost: String): Boolean
}

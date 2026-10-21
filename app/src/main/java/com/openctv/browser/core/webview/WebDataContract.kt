package com.openctv.browser.core.webview

import com.openctv.browser.core.common.Resource

/**
 * Données que le moteur WebView garde sur la TV pour les sites visités. Contrat du `core/webview` (seul endroit qui
 * touche à la WebView et à ses stockages) : les Réglages l'appellent sans connaître le moteur.
 *
 * Les deux effacements sont indépendants : l'échec de l'un n'empêche pas d'appeler l'autre.
 */
interface WebDataContract {
    /**
     * Cookies, stockage web (localStorage, IndexedDB, cache d'applications…) et identifiants d'authentification HTTP
     * de tous les sites : l'utilisateur est déconnecté partout. Les exceptions de sécurité, les zooms par site et
     * les favoris ne sont pas ici et ne sont pas touchés.
     */
    suspend fun clearSiteData(): Resource<Unit>

    /** Cache HTTP du moteur (images et fichiers temporaires). */
    suspend fun clearCache(): Resource<Unit>
}

/** Sans moteur (tests, écrans sans WebView) : rien à effacer. */
object NoWebData : WebDataContract {
    override suspend fun clearSiteData(): Resource<Unit> = Resource.Success(Unit)
    override suspend fun clearCache(): Resource<Unit> = Resource.Success(Unit)
}

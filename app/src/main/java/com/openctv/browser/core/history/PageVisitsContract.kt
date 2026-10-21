package com.openctv.browser.core.history

import com.openctv.browser.core.common.Resource

/**
 * Enregistrement des pages principales réellement affichées, côté navigateur. Contrat du `core/` : `features/browser`
 * l'appelle sans connaître `features/history`, qui le remplit (stockage local, jamais envoyé ailleurs).
 */
interface PageVisitsContract {
    /**
     * [url] vient d'être affichée avec son [title] (vide tant qu'il n'est pas connu). Les adresses qui ne sont pas des
     * pages web (`about:`, `data:`…) sont ignorées. Rappelée pour la même page quand son titre arrive : la visite est
     * alors complétée, pas dupliquée. Ne bloque pas le thread principal.
     */
    suspend fun recordVisit(url: String, title: String): Resource<Unit>
}

/** Sans historique (tests, écrans sans stockage) : rien n'est enregistré. */
object NoPageVisits : PageVisitsContract {
    override suspend fun recordVisit(url: String, title: String): Resource<Unit> = Resource.Success(Unit)
}

package com.openctv.browser.features.search.domain

import com.openctv.browser.core.common.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Recherches récentes, stockées uniquement sur la TV ([RecentSearchList.MaxSize] au plus, effaçables).
 * Exposé dans `domain/` pour que les autres features puissent le consommer sans importer `data/`.
 */
interface RecentSearchesContract {
    /** Plus récente d'abord ; `Loading` à l'abonnement, puis un `Success` à chaque changement. */
    fun getRecents(): Flow<Resource<List<String>>>

    /** Ajoute (ou remonte) [query] ; sans effet si elle est vide. */
    suspend fun record(query: String): Resource<Unit>

    suspend fun clear(): Resource<Unit>
}

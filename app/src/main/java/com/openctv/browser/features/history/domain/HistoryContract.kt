package com.openctv.browser.features.history.domain

import com.openctv.browser.core.common.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Historique des pages visitées, stocké uniquement sur l'appareil (Room), borné à
 * [HistoryPolicy.MAX_AGE] et [HistoryPolicy.MAX_ENTRIES]. L'enregistrement des visites passe par
 * `core/history/PageVisitsContract`, que l'implémentation remplit aussi.
 *
 * Flux réactif : `Loading` une fois à l'abonnement, puis un `Success` à chaque modification, ou un `Error` terminal.
 */
interface HistoryContract {
    /** Entrées de la plus récente à la plus ancienne. */
    fun getEntries(): Flow<Resource<List<HistoryEntry>>>

    suspend fun remove(id: Long): Resource<Unit>

    /** Efface tout l'historique (favoris, réglages et cookies ne sont pas touchés). */
    suspend fun clear(): Resource<Unit>
}

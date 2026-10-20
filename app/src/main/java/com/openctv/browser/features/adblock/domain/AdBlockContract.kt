package com.openctv.browser.features.adblock.domain

import com.openctv.browser.core.common.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * API du bloqueur de pubs pour l'UI (Réglages, menu du navigateur). Les sites sont désignés par leur
 * hôte ou une URL : `arte.tv`, `https://www.arte.tv/fr/` (un site désactivé couvre ses sous-domaines).
 */
interface AdBlockContract {

    /** État des listes de filtres : dernière mise à jour, mise à jour en cours, dernière erreur. */
    val filterLists: StateFlow<FilterListsState>

    /**
     * Charge la liste en cache puis la met à jour si elle est périmée, sans bloquer la navigation.
     * Appelée une fois au démarrage de l'application ; les appels suivants sont sans effet.
     */
    fun start()

    /**
     * Télécharge les listes maintenant. `Resource.Error` si le téléchargement échoue (l'ancienne liste
     * reste active), `Resource.Loading` si une mise à jour est déjà en cours.
     */
    suspend fun refreshNow(): Resource<Unit>

    /** `true` tant que le blocage n'est pas désactivé pour [site] (l'interrupteur global est dans les Réglages). */
    fun isEnabledForSite(site: String): Flow<Boolean>

    /** Persiste le choix, puis le moteur le reprend ; `Resource.Error` si le stockage échoue. */
    suspend fun setEnabledForSite(site: String, isEnabled: Boolean): Resource<Unit>

    /** Requêtes bloquées pour [site] depuis le lancement de l'application (en mémoire). */
    fun blockedCount(site: String): Flow<Int>
}

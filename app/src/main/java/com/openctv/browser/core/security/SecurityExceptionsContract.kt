package com.openctv.browser.core.security

import com.openctv.browser.core.common.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Exceptions de sécurité accordées par l'utilisateur (cf. [SiteException]) : de session (en mémoire, jusqu'à la
 * fermeture de l'app) et mémorisées (sur la TV uniquement, jamais envoyées nulle part).
 */
interface SecurityExceptionsContract {
    /** Exceptions mémorisées, triées par hôte (liste des Réglages). */
    val persisted: StateFlow<Resource<List<SiteException>>>

    /**
     * [exception] est-elle accordée (session ou mémorisée) ? Synchrone et sans I/O : appelée depuis les
     * callbacks de la WebView. Tant que les exceptions mémorisées ne sont pas lues, seules celles de session
     * comptent (côté sûr : on avertit plutôt que de laisser passer).
     */
    fun isAllowed(exception: SiteException): Boolean

    /**
     * Accorde [exception] pour la session ; [isRemembered] la mémorise aussi sur la TV. Un échec de
     * l'enregistrement laisse l'exception valable pour la session.
     */
    suspend fun allow(exception: SiteException, isRemembered: Boolean): Resource<Unit>

    /** Retire [exception] de la session et de la mémoire : la prochaine visite avertit de nouveau. */
    suspend fun remove(exception: SiteException): Resource<Unit>
}

/** Aucune exception, jamais : valeur par défaut des composants qui n'ont pas besoin du stockage (tests, aperçus). */
object NoSecurityExceptions : SecurityExceptionsContract {
    override val persisted: StateFlow<Resource<List<SiteException>>> = MutableStateFlow(Resource.Success(emptyList()))
    override fun isAllowed(exception: SiteException): Boolean = false
    override suspend fun allow(exception: SiteException, isRemembered: Boolean): Resource<Unit> = Resource.Success(Unit)
    override suspend fun remove(exception: SiteException): Resource<Unit> = Resource.Success(Unit)
}

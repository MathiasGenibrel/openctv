package com.openctv.browser.features.browser

import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.security.SecurityExceptionsContract
import com.openctv.browser.core.security.SiteException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Exceptions de sécurité en mémoire, qui retiennent ce que l'utilisateur a demandé. */
class FakeSecurityExceptions(vararg allowed: SiteException) : SecurityExceptionsContract {
    val allowed = allowed.toMutableSet()

    /** Derniers choix reçus : (exception, mémorisée ?). */
    val requests = mutableListOf<Pair<SiteException, Boolean>>()

    /** Non nul : l'enregistrement échoue avec ce message (l'exception reste valable pour la session). */
    var failure: String? = null

    private val _persisted = MutableStateFlow<Resource<List<SiteException>>>(Resource.Success(emptyList()))
    override val persisted: StateFlow<Resource<List<SiteException>>> = _persisted

    override fun isAllowed(exception: SiteException) = exception in allowed

    override suspend fun allow(exception: SiteException, isRemembered: Boolean): Resource<Unit> {
        requests += exception to isRemembered
        allowed += exception
        if (isRemembered) _persisted.value = Resource.Success(((_persisted.value as Resource.Success).data + exception).sortedBy { it.host })
        return failure?.let { Resource.Error(it) } ?: Resource.Success(Unit)
    }

    override suspend fun remove(exception: SiteException): Resource<Unit> {
        allowed -= exception
        _persisted.value = Resource.Success((_persisted.value as Resource.Success).data - exception)
        return Resource.Success(Unit)
    }
}

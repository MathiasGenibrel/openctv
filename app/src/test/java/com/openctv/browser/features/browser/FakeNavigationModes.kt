package com.openctv.browser.features.browser

import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.navmode.NavigationMode
import com.openctv.browser.core.navmode.NavigationModeContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Modes de navigation par site en mémoire, qui retiennent ce que l'utilisateur a demandé. */
class FakeNavigationModes(vararg initialElementHosts: String) : NavigationModeContract {
    /** Non nul : l'écriture échoue avec ce message. */
    var failure: String? = null

    private val _hosts = MutableStateFlow<Resource<Set<String>>>(Resource.Success(initialElementHosts.toSet()))
    override val elementHosts: StateFlow<Resource<Set<String>>> = _hosts

    val current: Set<String> get() = (_hosts.value as Resource.Success).data

    override suspend fun set(host: String, mode: NavigationMode): Resource<Unit> {
        failure?.let { return Resource.Error(it) }
        _hosts.value = Resource.Success(if (mode == NavigationMode.Elements) current + host else current - host)
        return Resource.Success(Unit)
    }
}

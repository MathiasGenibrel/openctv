package com.openctv.browser.features.browser

import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.zoom.PageZoom
import com.openctv.browser.core.zoom.PageZoomContract
import com.openctv.browser.core.zoom.SiteZoom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Zooms par site en mémoire, qui retiennent ce que l'utilisateur a demandé. */
class FakePageZoom(vararg initial: SiteZoom) : PageZoomContract {
    /** Non nul : l'écriture échoue avec ce message. */
    var failure: String? = null

    private val _sites = MutableStateFlow<Resource<List<SiteZoom>>>(Resource.Success(initial.sortedBy { it.host }))
    override val sites: StateFlow<Resource<List<SiteZoom>>> = _sites

    val current: List<SiteZoom> get() = (_sites.value as Resource.Success).data

    override suspend fun set(host: String, zoom: PageZoom): Resource<Unit> {
        failure?.let { return Resource.Error(it) }
        _sites.value = Resource.Success((current.filterNot { it.host == host } + SiteZoom(host, zoom)).sortedBy { it.host })
        return Resource.Success(Unit)
    }

    override suspend fun remove(host: String): Resource<Unit> {
        failure?.let { return Resource.Error(it) }
        _sites.value = Resource.Success(current.filterNot { it.host == host })
        return Resource.Success(Unit)
    }
}

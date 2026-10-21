package com.openctv.browser.features.settings.data

import com.openctv.browser.core.common.DispatcherProvider
import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.webview.WebNavigationPolicy
import com.openctv.browser.core.zoom.PageZoom
import com.openctv.browser.features.settings.domain.CursorSpeed
import com.openctv.browser.features.settings.domain.HudHideDelay
import com.openctv.browser.features.settings.domain.SearchEngine
import com.openctv.browser.features.settings.domain.Settings
import com.openctv.browser.features.settings.domain.SettingsContract
import com.openctv.browser.features.settings.domain.StartPage
import com.openctv.browser.features.settings.domain.orDefaults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** [scope] porte la lecture partagée et les écritures sans attente : il vit aussi longtemps que l'application. */
class SettingsImpl(
    private val store: SettingsStore,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) : SettingsContract {

    override val settings: StateFlow<Resource<Settings>> = store.settings
        .map<Settings, Resource<Settings>> { Resource.Success(it) }
        .catch { emit(Resource.Error(it.message)) }
        .flowOn(dispatchers.io)
        .stateIn(scope, SharingStarted.Eagerly, Resource.Loading)

    override suspend fun launchUrl(): String? {
        val loaded = settings.first { it !is Resource.Loading }
        return loaded.orDefaults().launchUrl()
    }

    override suspend fun setSearchEngine(engine: SearchEngine) = write { it.copy(searchEngine = engine) }

    override suspend fun setStartPage(page: StartPage) = write { it.copy(startPage = page) }

    override suspend fun setHudHideDelay(delay: HudHideDelay) = write { it.copy(hudHideDelay = delay) }

    override suspend fun setCursorSpeed(speed: CursorSpeed) = write { it.copy(cursorSpeed = speed) }

    override suspend fun setPageZoom(zoom: PageZoom) = write { it.copy(pageZoom = zoom) }

    override suspend fun setAdBlockEnabled(isEnabled: Boolean) = write { it.copy(isAdBlockEnabled = isEnabled) }

    override suspend fun setCookieBannerBlockEnabled(isEnabled: Boolean) =
        write { it.copy(isCookieBannerBlockEnabled = isEnabled) }

    override fun recordLastPage(url: String) {
        val isWebPage = WebNavigationPolicy.isUrlAllowed(url)
        if (!isWebPage) return
        scope.launch { write { it.copy(lastPage = url.trim()) } }
    }

    override suspend fun clearLastPage() = write { it.copy(lastPage = null) }

    private suspend fun write(transform: (Settings) -> Settings): Resource<Unit> =
        withContext(dispatchers.io) {
            try {
                store.update(transform)
                Resource.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Resource.Error(e.message)
            }
        }
}

package com.openctv.browser.features.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.zoom.PageZoom
import com.openctv.browser.core.common.TestDispatcherProvider
import com.openctv.browser.features.settings.data.SettingsImpl
import com.openctv.browser.features.settings.data.SettingsPreferencesCodec
import com.openctv.browser.features.settings.domain.CursorSpeed
import com.openctv.browser.features.settings.domain.HudHideDelay
import com.openctv.browser.features.settings.domain.SearchEngine
import com.openctv.browser.features.settings.domain.Settings
import com.openctv.browser.features.settings.domain.StartPage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsImplTest {

    private fun TestScope.impl(store: FakeSettingsStore) = SettingsImpl(
        store = store,
        dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
        scope = backgroundScope,
    )

    @Test
    fun `Given nothing stored When the app starts Then settings load with defaults and launch opens home`() = runTest {
        val repository = impl(FakeSettingsStore())

        assertThat(repository.launchUrl()).isNull()
        assertThat(repository.settings.value).isEqualTo(Resource.Success(Settings()))
    }

    @Test
    fun `Given a precise site as start page When the app starts Then launch returns that site`() = runTest {
        val repository = impl(FakeSettingsStore(Settings(startPage = StartPage.Site("https://arte.tv"))))

        assertThat(repository.launchUrl()).isEqualTo("https://arte.tv")
    }

    @Test
    fun `Given the user changes preferences When they are saved Then the exposed settings follow`() = runTest {
        val repository = impl(FakeSettingsStore())
        runCurrent()

        repository.setSearchEngine(SearchEngine.Qwant)
        repository.setHudHideDelay(HudHideDelay.Never)
        repository.setCursorSpeed(CursorSpeed.Fast)
        repository.setPageZoom(PageZoom.ThreeQuarters)
        repository.setAdBlockEnabled(false)
        repository.setStartPage(StartPage.LastPage)
        runCurrent()

        assertThat(repository.settings.value).isEqualTo(
            Resource.Success(
                Settings(
                    searchEngine = SearchEngine.Qwant,
                    startPage = StartPage.LastPage,
                    hudHideDelay = HudHideDelay.Never,
                    cursorSpeed = CursorSpeed.Fast,
                    pageZoom = PageZoom.ThreeQuarters,
                    isAdBlockEnabled = false,
                ),
            ),
        )
    }

    @Test
    fun `Given the settings are still being read When a consumer reads the current settings Then the defaults apply`() = runTest {
        val repository = impl(FakeSettingsStore(Settings(isAdBlockEnabled = false)))

        assertThat(repository.settings.value).isEqualTo(Resource.Loading)
        assertThat(repository.current).isEqualTo(Settings())
    }

    @Test
    fun `Given the ad blocker turned off When a request is intercepted Then the current settings say it is off without suspending`() = runTest {
        val repository = impl(FakeSettingsStore())
        runCurrent()

        repository.setAdBlockEnabled(false)
        runCurrent()

        assertThat(repository.current.isAdBlockEnabled).isFalse()
    }

    @Test
    fun `Given a web page finished loading When the browser records it Then resume last page can reopen it`() = runTest {
        val store = FakeSettingsStore(Settings(startPage = StartPage.LastPage))
        val repository = impl(store)

        repository.recordLastPage("https://arte.tv/fr")
        runCurrent()

        assertThat(repository.launchUrl()).isEqualTo("https://arte.tv/fr")
    }

    @Test
    fun `Given a non web address When the browser records it Then the last page is left untouched`() = runTest {
        val store = FakeSettingsStore(Settings(lastPage = "https://arte.tv"))
        val repository = impl(store)

        repository.recordLastPage("intent://scan/#Intent;end")
        repository.recordLastPage("about:blank")
        runCurrent()

        assertThat(store.current.value.lastPage).isEqualTo("https://arte.tv")
    }

    @Test
    fun `Given the storage fails When saving a preference Then an error is returned and the value is kept`() = runTest {
        val store = FakeSettingsStore().apply { failure = java.io.IOException("disque plein") }
        val repository = impl(store)

        val result = repository.setSearchEngine(SearchEngine.Google)

        assertThat(result).isEqualTo(Resource.Error("disque plein"))
        assertThat(store.current.value.searchEngine).isEqualTo(SearchEngine.DuckDuckGo)
    }

    @Test
    fun `Given every setting changed When encoded then decoded Then nothing is lost`() {
        val settings = Settings(
            searchEngine = SearchEngine.BraveSearch,
            startPage = StartPage.Site("https://arte.tv"),
            hudHideDelay = HudHideDelay.Seconds10,
            cursorSpeed = CursorSpeed.Slow,
            pageZoom = PageZoom.Half,
            isAdBlockEnabled = false,
            lastPage = "https://wikipedia.org/wiki/Kotlin",
        )
        val preferences = mutablePreferencesOf()

        SettingsPreferencesCodec.encode(settings, into = preferences)

        assertThat(SettingsPreferencesCodec.decode(preferences)).isEqualTo(settings)
    }

    @Test
    fun `Given corrupted stored values When decoding Then defaults replace them`() {
        val preferences = preferencesOf(
            stringPreferencesKey("search_engine") to "altavista",
            stringPreferencesKey("start_page") to "site:javascript://alert(1)",
            stringPreferencesKey("cursor_speed") to "warp",
            stringPreferencesKey("page_zoom") to "10",
        )

        assertThat(SettingsPreferencesCodec.decode(preferences)).isEqualTo(Settings())
    }
}

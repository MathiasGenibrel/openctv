package com.openctv.browser.features.adblock

import android.webkit.WebResourceResponse
import com.google.common.truth.Truth.assertThat
import com.openctv.browser.features.adblock.data.AdBlockInterceptor
import com.openctv.browser.features.adblock.data.FilterListParser
import com.openctv.browser.features.adblock.domain.BlockedRequestCounter
import com.openctv.browser.features.adblock.domain.DefaultAdBlockEngine
import com.openctv.browser.features.settings.domain.Settings
import com.openctv.browser.features.settings.domain.SettingsContract
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import com.openctv.browser.core.common.Resource
import org.junit.Test

class AdBlockInterceptorTest {

    private val engine = DefaultAdBlockEngine().apply {
        updateFilters(FilterListParser.parse("||ads.example.com^\n||site.org^\n"))
    }
    private val counter = BlockedRequestCounter()
    private val emptyResponse = mockk<WebResourceResponse>()
    private val settingsState = MutableStateFlow<Resource<Settings>>(Resource.Success(Settings(isAdBlockEnabled = true)))
    private val settings = mockk<SettingsContract> {
        every { current } answers { (settingsState.value as Resource.Success).data }
    }
    private val interceptor = AdBlockInterceptor(engine, settings, counter) { emptyResponse }

    private val page = "https://news.example.org/article"

    @Test
    fun `Given a listed ad When a sub-resource requests it Then the response is empty and the site counter grows`() {
        // When
        val response = interceptor.intercept("https://ads.example.com/banner.js", page, isMainFrame = false)

        // Then
        assertThat(response).isSameInstanceAs(emptyResponse)
        assertThat(counter.countOf(page)).isEqualTo(1)
    }

    @Test
    fun `Given a listed domain When the user opens it as the main page Then the page is never blocked`() {
        // When
        val response = interceptor.intercept("https://site.org/", pageUrl = null, isMainFrame = true)

        // Then
        assertThat(response).isNull()
        assertThat(counter.countOf("https://site.org/")).isEqualTo(0)
    }

    @Test
    fun `Given an unlisted request When it is intercepted Then it passes through`() {
        // When
        val response = interceptor.intercept("https://cdn.example.org/app.js", page, isMainFrame = false)

        // Then
        assertThat(response).isNull()
        assertThat(counter.countOf(page)).isEqualTo(0)
    }

    @Test
    fun `Given the global ad blocker is off When a listed ad is requested Then nothing is blocked`() {
        // Given
        settingsState.value = Resource.Success(Settings(isAdBlockEnabled = false))

        // When
        val response = interceptor.intercept("https://ads.example.com/banner.js", page, isMainFrame = false)

        // Then
        assertThat(response).isNull()
    }

    @Test
    fun `Given blocking disabled for the current site When a listed ad is requested Then nothing is blocked`() {
        // Given
        engine.updateDisabledSites(listOf("news.example.org"))

        // When
        val response = interceptor.intercept("https://ads.example.com/banner.js", page, isMainFrame = false)

        // Then
        assertThat(response).isNull()
        assertThat(counter.countOf(page)).isEqualTo(0)
    }

    @Test
    fun `Given no filter list yet When any request is intercepted Then everything passes through`() {
        // Given
        val emptyEngine = DefaultAdBlockEngine()
        val firstLaunch = AdBlockInterceptor(emptyEngine, settings, counter) { emptyResponse }

        // When
        val response = firstLaunch.intercept("https://ads.example.com/banner.js", page, isMainFrame = false)

        // Then
        assertThat(response).isNull()
    }
}

package com.openctv.browser.features.settings

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.common.TestDispatcherProvider
import com.openctv.browser.features.settings.domain.ClearBrowsingData
import com.openctv.browser.features.settings.domain.ClearDataCounts
import com.openctv.browser.features.settings.domain.ClearDataSelection
import com.openctv.browser.features.settings.domain.ClearDataStep
import com.openctv.browser.features.settings.domain.Settings
import com.openctv.browser.features.settings.domain.SettingsContract
import com.openctv.browser.features.settings.domain.StartPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClearBrowsingDataTest {

    private val history = FakeHistory("https://arte.tv", "https://wikipedia.org")
    private val recents = FakeRecentSearches("chats", "météo")
    private val webData = FakeWebData()

    private fun TestScope.settings() = settingsImpl(
        scope = backgroundScope,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)),
        initial = Settings(startPage = StartPage.LastPage, lastPage = "https://arte.tv/fr"),
    )

    private fun TestScope.clearData(settings: SettingsContract = settings()) =
        ClearBrowsingData(history, recents, settings, webData)

    @Test
    fun `Given every category selected When the user clears Then history, searches, site data and cache are all gone`() = runTest {
        // Given
        val clear = clearData()

        // When
        val result = clear(ClearDataSelection())

        // Then
        assertThat(result.isSuccess).isTrue()
        assertThat(history.entries.value).isEmpty()
        assertThat(recents.queries.value).isEmpty()
        assertThat(webData.hasSiteData).isFalse()
        assertThat(webData.hasCache).isFalse()
    }

    @Test
    fun `Given only the cache selected When the user clears Then history, searches and cookies are kept`() = runTest {
        // Given
        val clear = clearData()
        val selection = ClearDataSelection(setOf(ClearDataStep.Cache))

        // When
        val result = clear(selection)

        // Then
        assertThat(result.isSuccess).isTrue()
        assertThat(webData.hasCache).isFalse()
        assertThat(webData.hasSiteData).isTrue()
        assertThat(history.entries.value).hasSize(2)
        assertThat(recents.queries.value).containsExactly("chats", "météo")
    }

    @Test
    fun `Given the cookies step fails When the user clears everything Then the other steps still run and only cookies are reported`() = runTest {
        // Given
        webData.siteDataFailure = "storage unavailable"
        val clear = clearData()

        // When
        val result = clear(ClearDataSelection())

        // Then
        assertThat(result.failed).containsExactly(ClearDataStep.SiteData)
        assertThat(history.entries.value).isEmpty()
        assertThat(recents.queries.value).isEmpty()
        assertThat(webData.hasCache).isFalse()
        assertThat(webData.hasSiteData).isTrue()
    }

    @Test
    fun `Given history and searches both fail When the user clears Then both are reported and the web data is still erased`() = runTest {
        // Given
        history.failure = "disk full"
        recents.failure = "datastore"
        val clear = clearData()

        // When
        val result = clear(ClearDataSelection())

        // Then
        assertThat(result.failed).containsExactly(ClearDataStep.History, ClearDataStep.RecentSearches)
        assertThat(webData.hasSiteData).isFalse()
        assertThat(webData.hasCache).isFalse()
    }

    @Test
    fun `Given resume-last-page with a remembered page When the history is cleared Then launch falls back to the home screen`() = runTest {
        // Given
        val settings = settings()
        assertThat(settings.launchUrl()).isEqualTo("https://arte.tv/fr")

        // When
        clearData(settings)(ClearDataSelection(setOf(ClearDataStep.History)))

        // Then
        assertThat(settings.launchUrl()).isNull()
    }

    @Test
    fun `Given a remembered last page When only the cache is cleared Then the last page is kept`() = runTest {
        // Given
        val settings = settings()

        // When
        clearData(settings)(ClearDataSelection(setOf(ClearDataStep.Cache)))

        // Then
        assertThat(settings.launchUrl()).isEqualTo("https://arte.tv/fr")
    }

    @Test
    fun `Given the history cannot be erased When the user clears Then the last page is still forgotten`() = runTest {
        // Given
        history.failure = "disk full"
        val settings = settings()

        // When
        val result = clearData(settings)(ClearDataSelection(setOf(ClearDataStep.History)))

        // Then
        assertThat(result.failed).containsExactly(ClearDataStep.History)
        assertThat(settings.launchUrl()).isNull()
    }

    @Test
    fun `Given clearing keeps the other settings When the user clears everything Then start page and other preferences are unchanged`() = runTest {
        // Given
        val settings = settings()

        // When
        clearData(settings)(ClearDataSelection())

        // Then
        assertThat(settings.current.startPage).isEqualTo(StartPage.LastPage)
        assertThat(settings.current.lastPage).isNull()
    }

    @Test
    fun `Given clearing has started When the screen that asked for it goes away Then every selected category is still erased`() = runTest {
        // Given
        val gate = CompletableDeferred<Unit>()
        webData.siteDataGate = gate
        val clear = clearData()
        val request = launch { clear(ClearDataSelection()) }
        runCurrent()

        // When
        request.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        // Then
        assertThat(history.entries.value).isEmpty()
        assertThat(recents.queries.value).isEmpty()
        assertThat(webData.hasSiteData).isFalse()
        assertThat(webData.hasCache).isFalse()
    }

    @Test
    fun `Given a history and recent searches When everything is cleared Then both counts drop to zero`() = runTest {
        // Given
        val clear = clearData()
        assertThat(clear.counts.first()).isEqualTo(ClearDataCounts(history = 2, recentSearches = 2))

        // When
        clear(ClearDataSelection())

        // Then
        assertThat(clear.counts.first()).isEqualTo(ClearDataCounts(history = 0, recentSearches = 0))
    }
}

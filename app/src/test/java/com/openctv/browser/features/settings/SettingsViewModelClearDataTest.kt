package com.openctv.browser.features.settings

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.common.TestDispatcherProvider
import com.openctv.browser.core.security.SiteException
import com.openctv.browser.core.zoom.PageZoom
import com.openctv.browser.core.zoom.SiteZoom
import com.openctv.browser.features.browser.FakeBookmarks
import com.openctv.browser.features.browser.FakePageZoom
import com.openctv.browser.features.browser.FakeSecurityExceptions
import com.openctv.browser.features.settings.domain.ClearBrowsingData
import com.openctv.browser.features.settings.domain.ClearDataSelection
import com.openctv.browser.features.settings.domain.ClearDataStep
import com.openctv.browser.features.settings.domain.Settings
import com.openctv.browser.features.settings.domain.StartPage
import com.openctv.browser.features.settings.ui.ClearOutcome
import com.openctv.browser.features.settings.ui.SettingsPanel
import com.openctv.browser.features.settings.ui.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelClearDataTest {

    private val history = FakeHistory("https://arte.tv", "https://wikipedia.org", "https://example.org")
    private val recents = FakeRecentSearches("chats")
    private val webData = FakeWebData()
    private val bookmarks = FakeBookmarks().apply { seed("https://arte.tv") }
    private val exceptions = FakeSecurityExceptions()
    private val zooms = FakePageZoom(SiteZoom("arte.tv", PageZoom.Half))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** ViewModel abonné (l'état n'est calculé que tant qu'il est observé, comme à l'écran). */
    private fun TestScope.viewModel(): SettingsViewModel {
        val dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler))
        val settings = settingsImpl(backgroundScope, dispatchers, Settings(startPage = StartPage.LastPage, lastPage = "https://arte.tv"))
        return SettingsViewModel(
            settings = settings,
            bookmarks = bookmarks,
            securityExceptions = exceptions,
            siteZooms = zooms,
            clearBrowsingData = ClearBrowsingData(history, recents, settings, webData),
            history = history,
            recentSearches = recents,
        ).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        }
    }

    @Test
    fun `Given the user opens the panel When it shows Then every category is checked and counts are displayed`() = runTest {
        // Given
        val viewModel = viewModel()

        // When
        viewModel.open(SettingsPanel.ClearData)

        // Then
        val state = viewModel.uiState.value
        assertThat(state.clearSelection.steps).containsExactlyElementsIn(ClearDataStep.entries)
        assertThat(state.historyCount).isEqualTo(3)
        assertThat(state.recentSearchCount).isEqualTo(1)
    }

    @Test
    fun `Given every box is unchecked When the selection is read Then there is nothing to clear and the button must be inactive`() = runTest {
        // Given
        val viewModel = viewModel()
        viewModel.open(SettingsPanel.ClearData)

        // When
        ClearDataStep.entries.forEach(viewModel::toggleClearStep)
        viewModel.clearData()

        // Then
        assertThat(viewModel.uiState.value.clearSelection.isEmpty).isTrue()
        assertThat(history.entries.value).hasSize(3)
        assertThat(webData.hasCache).isTrue()
        assertThat(viewModel.uiState.value.clearOutcome).isNull()
    }

    @Test
    fun `Given a previous visit left boxes unchecked When the panel is reopened Then everything is checked again`() = runTest {
        // Given
        val viewModel = viewModel()
        viewModel.open(SettingsPanel.ClearData)
        viewModel.toggleClearStep(ClearDataStep.History)
        viewModel.closePanel()

        // When
        viewModel.open(SettingsPanel.ClearData)

        // Then
        assertThat(viewModel.uiState.value.clearSelection).isEqualTo(ClearDataSelection())
    }

    @Test
    fun `Given history unchecked When the user clears Then the history stays and the others are erased`() = runTest {
        // Given
        val viewModel = viewModel()
        viewModel.open(SettingsPanel.ClearData)
        viewModel.toggleClearStep(ClearDataStep.History)

        // When
        viewModel.clearData()

        // Then
        assertThat(history.entries.value).hasSize(3)
        assertThat(recents.queries.value).isEmpty()
        assertThat(webData.hasSiteData).isFalse()
        assertThat(webData.hasCache).isFalse()
        assertThat(viewModel.uiState.value.clearOutcome).isEqualTo(ClearOutcome.Done)
    }

    @Test
    fun `Given the user clears everything When it is done Then the panel closes so focus returns to the settings row`() = runTest {
        // Given
        val viewModel = viewModel()
        viewModel.open(SettingsPanel.ClearData)

        // When
        viewModel.clearData()

        // Then
        assertThat(viewModel.uiState.value.openPanel).isNull()
        assertThat(viewModel.uiState.value.clearOutcome).isEqualTo(ClearOutcome.Done)
    }

    @Test
    fun `Given the result was shown When the snackbar times out Then it disappears`() = runTest {
        // Given
        val viewModel = viewModel()
        viewModel.open(SettingsPanel.ClearData)
        viewModel.clearData()

        // When
        viewModel.onClearOutcomeShown()

        // Then
        assertThat(viewModel.uiState.value.clearOutcome).isNull()
    }

    @Test
    fun `Given the cache step fails When the user clears Then the snackbar names the cache and other data is erased`() = runTest {
        // Given
        webData.cacheFailure = "no webview"
        val viewModel = viewModel()
        viewModel.open(SettingsPanel.ClearData)

        // When
        viewModel.clearData()

        // Then
        assertThat(viewModel.uiState.value.clearOutcome).isEqualTo(ClearOutcome.Failed(setOf(ClearDataStep.Cache)))
        assertThat(history.entries.value).isEmpty()
        assertThat(webData.hasSiteData).isFalse()
        assertThat(viewModel.uiState.value.openPanel).isNull()
    }

    @Test
    fun `Given bookmarks, security exceptions, zooms and settings When the user clears everything Then they are all kept`() = runTest {
        // Given
        val exception = SiteException.Http("neuf.example")
        exceptions.allow(exception, isRemembered = true)
        val viewModel = viewModel()
        viewModel.open(SettingsPanel.ClearData)

        // When
        viewModel.clearData()

        // Then
        val state = viewModel.uiState.value
        assertThat(bookmarks.urls).containsExactly("https://arte.tv")
        assertThat(state.bookmarks).hasSize(1)
        assertThat(state.securityExceptions).containsExactly(exception)
        assertThat(zooms.current).containsExactly(SiteZoom("arte.tv", PageZoom.Half))
        assertThat(state.settings.startPage).isEqualTo(StartPage.LastPage)
        assertThat(state.settings.lastPage).isNull()
    }
}

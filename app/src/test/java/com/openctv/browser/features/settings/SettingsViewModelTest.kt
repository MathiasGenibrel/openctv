package com.openctv.browser.features.settings

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.common.TestDispatcherProvider
import com.openctv.browser.core.security.NoSecurityExceptions
import com.openctv.browser.core.security.SecurityExceptionsContract
import com.openctv.browser.core.security.SiteException
import com.openctv.browser.core.zoom.PageZoom
import com.openctv.browser.core.zoom.PageZoomContract
import com.openctv.browser.core.zoom.SiteZoom
import com.openctv.browser.features.browser.FakePageZoom
import com.openctv.browser.features.browser.FakeSecurityExceptions
import com.openctv.browser.features.bookmarks.domain.Bookmark
import com.openctv.browser.features.bookmarks.domain.BookmarksContract
import com.openctv.browser.features.settings.data.SettingsImpl
import com.openctv.browser.features.settings.domain.SearchEngine
import com.openctv.browser.features.settings.domain.StartPage
import com.openctv.browser.features.settings.ui.SettingsPanel
import com.openctv.browser.features.settings.ui.SettingsViewModel
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val bookmarks = listOf(Bookmark("https://arte.tv", "Arte", Instant.EPOCH))
    private val bookmarksContract = object : BookmarksContract {
        override fun getBookmarks(): Flow<Resource<List<Bookmark>>> = flowOf(Resource.Success(bookmarks))
        override fun isBookmarked(url: String): Flow<Resource<Boolean>> = flowOf(Resource.Success(false))
        override suspend fun add(url: String, title: String) = Resource.Success(Unit)
        override suspend fun remove(url: String) = Resource.Success(Unit)
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        store: FakeSettingsStore = FakeSettingsStore(),
        securityExceptions: SecurityExceptionsContract = NoSecurityExceptions,
        siteZooms: PageZoomContract = FakePageZoom(),
    ): SettingsViewModel {
        val settings = SettingsImpl(
            store = store,
            dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
            scope = backgroundScope,
        )
        return SettingsViewModel(settings, bookmarksContract, securityExceptions, siteZooms)
    }

    /** `uiState` n'est actif qu'abonné (WhileSubscribed) : on s'abonne pour la durée du test. */
    private fun TestScope.collected(viewModel: SettingsViewModel) = viewModel.uiState.also {
        backgroundScope.launch { it.collect {} }
    }

    @Test
    fun `Given a sub panel is open When the user presses Back Then the panel closes first and Back is consumed`() = runTest {
        val viewModel = viewModel()
        viewModel.open(SettingsPanel.SearchEngine)

        val consumed = viewModel.onBack()

        assertThat(consumed).isTrue()
        assertThat(collected(viewModel).value.openPanel).isNull()
    }

    @Test
    fun `Given no sub panel When the user presses Back Then Back is not consumed so the screen returns home`() = runTest {
        val viewModel = viewModel()

        assertThat(viewModel.onBack()).isFalse()
    }

    @Test
    fun `Given a sub panel open When the user presses Back twice Then the first closes the panel and the second returns home`() = runTest {
        val viewModel = viewModel()
        viewModel.open(SettingsPanel.StartPage)

        val firstBackConsumed = viewModel.onBack()
        val secondBackConsumed = viewModel.onBack()

        assertThat(firstBackConsumed).isTrue()
        assertThat(secondBackConsumed).isFalse()
    }

    @Test
    fun `Given the settings screen When the user picks a favorite as start page Then it is saved as a precise site`() = runTest {
        val store = FakeSettingsStore()
        val viewModel = viewModel(store)
        collected(viewModel)
        runCurrent()

        viewModel.selectStartSite(bookmarks.first().url)
        runCurrent()

        assertThat(store.current.value.startPage).isEqualTo(StartPage.Site("https://arte.tv"))
        assertThat(viewModel.uiState.value.bookmarks).isEqualTo(bookmarks)
    }

    @Test
    fun `Given an invalid typed address When validating Then nothing is saved and the error is shown`() = runTest {
        val store = FakeSettingsStore()
        val viewModel = viewModel(store)
        collected(viewModel)
        runCurrent()

        viewModel.selectStartSite("pas une adresse")
        runCurrent()

        assertThat(store.current.value.startPage).isEqualTo(StartPage.Home)
        assertThat(viewModel.uiState.value.isSiteInputInvalid).isTrue()
    }

    @Test
    fun `Given the storage fails When the user picks a search engine Then the screen flags the failed save`() = runTest {
        val store = FakeSettingsStore().apply { failure = java.io.IOException("plein") }
        val viewModel = viewModel(store)
        collected(viewModel)
        runCurrent()

        viewModel.selectSearchEngine(SearchEngine.Google)
        runCurrent()

        assertThat(viewModel.uiState.value.isSaveFailed).isTrue()
        assertThat(viewModel.uiState.value.settings.searchEngine).isEqualTo(SearchEngine.DuckDuckGo)
    }

    @Test
    fun `Given remembered security exceptions When the user removes one in Settings Then it disappears and the others stay`() = runTest {
        val exceptions = FakeSecurityExceptions()
        val http = SiteException.Http("192.168.1.10")
        val certificate = SiteException.Certificate("nas.maison", "ab12")
        exceptions.allow(http, isRemembered = true)
        exceptions.allow(certificate, isRemembered = true)
        val viewModel = viewModel(securityExceptions = exceptions)
        collected(viewModel)
        runCurrent()
        assertThat(viewModel.uiState.value.securityExceptions).containsExactly(http, certificate).inOrder()

        viewModel.removeSecurityException(http)
        runCurrent()

        assertThat(viewModel.uiState.value.securityExceptions).containsExactly(certificate)
        assertThat(exceptions.isAllowed(http)).isFalse()
    }

    @Test
    fun `Given the zoom panel When the user picks 75 percent Then it becomes the default level for sites without their own`() = runTest {
        val store = FakeSettingsStore()
        val viewModel = viewModel(store)
        collected(viewModel)
        runCurrent()

        viewModel.open(SettingsPanel.PageZoom)
        viewModel.selectPageZoom(PageZoom.ThreeQuarters)
        runCurrent()

        assertThat(store.current.value.pageZoom).isEqualTo(PageZoom.ThreeQuarters)
        assertThat(viewModel.uiState.value.settings.pageZoom).isEqualTo(PageZoom.ThreeQuarters)
        assertThat(viewModel.uiState.value.openPanel).isEqualTo(SettingsPanel.PageZoom)
    }

    @Test
    fun `Given sites with their own zoom When the user removes one in Settings Then it goes back to the default and the others stay`() = runTest {
        val zooms = FakePageZoom(SiteZoom("arte.tv", PageZoom.TwoThirds), SiteZoom("gitlab-dashboard.tools.internal", PageZoom.Half))
        val viewModel = viewModel(siteZooms = zooms)
        collected(viewModel)
        runCurrent()
        assertThat(viewModel.uiState.value.siteZooms.map { it.host })
            .containsExactly("arte.tv", "gitlab-dashboard.tools.internal").inOrder()

        viewModel.removeSiteZoom("gitlab-dashboard.tools.internal")
        runCurrent()

        assertThat(viewModel.uiState.value.siteZooms).containsExactly(SiteZoom("arte.tv", PageZoom.TwoThirds))
    }

    @Test
    fun `Given the storage fails When the user removes a site zoom Then the screen flags the failed save and the chip stays`() = runTest {
        val zooms = FakePageZoom(SiteZoom("arte.tv", PageZoom.TwoThirds)).apply { failure = "disk" }
        val viewModel = viewModel(siteZooms = zooms)
        collected(viewModel)
        runCurrent()

        viewModel.removeSiteZoom("arte.tv")
        runCurrent()

        assertThat(viewModel.uiState.value.isSaveFailed).isTrue()
        assertThat(viewModel.uiState.value.siteZooms).hasSize(1)
    }
}

package com.openctv.browser.features.browser

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.common.TestDispatcherProvider
import com.openctv.browser.core.navmode.NavigationMode
import com.openctv.browser.core.zoom.PageZoom
import com.openctv.browser.features.adblock.domain.AdBlockContract
import com.openctv.browser.features.adblock.domain.FilterListsState
import com.openctv.browser.features.browser.domain.AdBlockStatus
import com.openctv.browser.features.browser.domain.BrowserMenuPanel
import com.openctv.browser.features.browser.domain.MenuWriteFailure
import com.openctv.browser.features.browser.ui.BrowserMenuViewModel
import com.openctv.browser.features.settings.FakeSettingsStore
import com.openctv.browser.features.settings.data.SettingsImpl
import com.openctv.browser.features.settings.domain.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserMenuViewModelTest {

    private val url = "https://www.arte.tv/fr/"
    private val bookmarks = FakeBookmarks()
    private val adBlock = FakeAdBlock()
    private val failures = mutableListOf<MenuWriteFailure>()
    private val pageZoom = MutableStateFlow(PageZoom.Full)
    private val chosenZooms = mutableListOf<PageZoom>()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(store: FakeSettingsStore = FakeSettingsStore()): BrowserMenuViewModel {
        val settings = SettingsImpl(
            store = store,
            dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
            scope = backgroundScope,
        )
        return BrowserMenuViewModel(
            MutableStateFlow(url), settings, adBlock, bookmarks, onWriteFailed = { failures += it },
            pageZoom = pageZoom, onZoomChosen = { chosenZooms += it },
        )
            .also { viewModel -> backgroundScope.launch { viewModel.state.collect {} } }
            .also { runCurrent() }
    }

    @Test
    fun `Given a site in cursor mode When the user chooses Navigation Then the browser is asked to switch the mode`() = runTest {
        val modes = MutableStateFlow(NavigationMode.Cursor)
        var toggles = 0
        val settings = SettingsImpl(
            store = FakeSettingsStore(),
            dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
            scope = backgroundScope,
        )
        val viewModel = BrowserMenuViewModel(
            MutableStateFlow(url), settings, adBlock, bookmarks, onWriteFailed = {},
            navigationMode = modes, onNavigationToggled = { toggles++ },
        ).also { viewModel -> backgroundScope.launch { viewModel.state.collect {} } }
        runCurrent()
        assertThat(viewModel.state.value.navigationMode).isEqualTo(NavigationMode.Cursor)

        viewModel.toggleNavigationMode()
        modes.value = NavigationMode.Elements
        runCurrent()

        assertThat(toggles).isEqualTo(1)
        assertThat(viewModel.state.value.navigationMode).isEqualTo(NavigationMode.Elements)
    }

    @Test
    fun `Given a page with blocked requests When the menu opens Then it names the site and shows the count`() = runTest {
        adBlock.blocked.value = 12
        val viewModel = viewModel()

        assertThat(viewModel.state.value.site).isEqualTo("arte.tv")
        assertThat(viewModel.state.value.adBlock).isEqualTo(AdBlockStatus.Active(12))
    }

    @Test
    fun `Given the blocker is on for the site When the user toggles it Then the site is excluded and the menu says so`() = runTest {
        val viewModel = viewModel()

        viewModel.toggleAdBlock()
        runCurrent()

        assertThat(adBlock.disabledSites).containsExactly(url)
        assertThat(viewModel.state.value.adBlock).isEqualTo(AdBlockStatus.DisabledForSite)

        viewModel.toggleAdBlock()
        runCurrent()

        assertThat(adBlock.disabledSites).isEmpty()
        assertThat(viewModel.state.value.adBlock).isInstanceOf(AdBlockStatus.Active::class.java)
    }

    @Test
    fun `Given the blocker is off for all sites When the menu opens Then it says so and the site switch changes nothing`() = runTest {
        val viewModel = viewModel(FakeSettingsStore(Settings(isAdBlockEnabled = false)))

        viewModel.toggleAdBlock()
        runCurrent()

        assertThat(viewModel.state.value.adBlock).isEqualTo(AdBlockStatus.DisabledEverywhere)
        assertThat(adBlock.disabledSites).isEmpty()
    }

    @Test
    fun `Given storage fails When the user toggles the blocker Then a notice is raised and the state is unchanged`() = runTest {
        adBlock.failure = "disk"
        val viewModel = viewModel()

        viewModel.toggleAdBlock()
        runCurrent()

        assertThat(failures).containsExactly(MenuWriteFailure.AdBlock)
        assertThat(viewModel.state.value.isAdBlockActive).isTrue()
    }

    @Test
    fun `Given a page that is not a favorite When the user toggles the favorite twice Then it is added then removed`() = runTest {
        val viewModel = viewModel()

        viewModel.toggleBookmark()
        runCurrent()
        assertThat(viewModel.state.value.isBookmarked).isTrue()
        assertThat(bookmarks.urls).hasSize(1)

        viewModel.toggleBookmark()
        runCurrent()
        assertThat(viewModel.state.value.isBookmarked).isFalse()
        assertThat(bookmarks.urls).isEmpty()
    }

    @Test
    fun `Given favorites storage fails When the user adds the page Then a notice is raised`() = runTest {
        bookmarks.failure = "disk"
        val viewModel = viewModel()

        viewModel.toggleBookmark()
        runCurrent()

        assertThat(failures).containsExactly(MenuWriteFailure.Bookmark)
        assertThat(viewModel.state.value.isBookmarked).isFalse()
    }

    @Test
    fun `Given a sub-panel is open When the user presses Back Then the sub-panel closes first and then Back is left to the menu`() = runTest {
        val viewModel = viewModel()
        viewModel.openPanel(BrowserMenuPanel.PageZoom)
        assertThat(viewModel.state.value.openPanel).isEqualTo(BrowserMenuPanel.PageZoom)

        val firstBackHandled = viewModel.onBack()
        val secondBackHandled = viewModel.onBack()

        assertThat(firstBackHandled).isTrue()
        assertThat(viewModel.state.value.openPanel).isNull()
        assertThat(secondBackHandled).isFalse()
    }

    @Test
    fun `Given a site zoomed to 50 percent When the menu opens Then the zoom row shows 50 for that exact host`() = runTest {
        pageZoom.value = PageZoom.Half
        val viewModel = viewModel()

        assertThat(viewModel.state.value.pageZoom).isEqualTo(PageZoom.Half)
        assertThat(viewModel.state.value.zoomHost).isEqualTo("www.arte.tv")
    }

    @Test
    fun `Given the zoom sub menu When the user picks a level Then the browser is told the level and the sub menu closes`() = runTest {
        val viewModel = viewModel(FakeSettingsStore(Settings(pageZoom = PageZoom.ThreeQuarters)))

        viewModel.openPanel(BrowserMenuPanel.PageZoom)
        viewModel.selectPageZoom(PageZoom.Half)

        assertThat(viewModel.state.value.openPanel).isNull()
        assertThat(chosenZooms).containsExactly(PageZoom.Half)
        assertThat(viewModel.state.value.defaultZoom).isEqualTo(PageZoom.ThreeQuarters)
    }
}

private class FakeAdBlock : AdBlockContract {
    val disabledSites = mutableSetOf<String>()
    private val disabled = MutableStateFlow<Set<String>>(emptySet())
    val blocked = MutableStateFlow(0)
    var failure: String? = null

    override val filterLists: StateFlow<FilterListsState> = MutableStateFlow(FilterListsState()).asStateFlow()

    override fun start() = Unit

    override suspend fun refreshNow(): Resource<Unit> = Resource.Success(Unit)

    override fun isEnabledForSite(site: String): Flow<Boolean> = disabled.map { site !in it }

    override suspend fun setEnabledForSite(site: String, isEnabled: Boolean): Resource<Unit> {
        failure?.let { return Resource.Error(it) }
        if (isEnabled) disabledSites.remove(site) else disabledSites.add(site)
        disabled.value = disabledSites.toSet()
        return Resource.Success(Unit)
    }

    override fun blockedCount(site: String): Flow<Int> = blocked
}

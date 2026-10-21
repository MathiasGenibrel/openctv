package com.openctv.browser.features.browser

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.zoom.PageZoom
import com.openctv.browser.core.zoom.SiteZoom
import com.openctv.browser.features.browser.domain.BrowserNotice
import com.openctv.browser.features.browser.ui.BrowserCommand
import com.openctv.browser.features.browser.ui.BrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserPageZoomTest {
    private val dashboard = "http://gitlab-dashboard.tools.internal/"
    private val arte = "https://www.arte.tv/fr/"
    private val bookmarks = FakeBookmarks()
    private val defaultZoom = MutableStateFlow(PageZoom.Full)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(url: String, zooms: FakePageZoom = FakePageZoom()) =
        BrowserViewModel(url, bookmarks, siteZooms = zooms, defaultZoom = defaultZoom)

    @Test
    fun `Given a site remembered at 50 percent When it opens Then the page is shown at 50 percent from the start`() {
        val viewModel = viewModel(dashboard, FakePageZoom(SiteZoom("gitlab-dashboard.tools.internal", PageZoom.Half)))

        assertThat(viewModel.pageZoom.value).isEqualTo(PageZoom.Half)
    }

    @Test
    fun `Given a site zoomed to 50 percent When the user opens another site Then it shows at the default level and back again at 50`() {
        val viewModel = viewModel(dashboard, FakePageZoom(SiteZoom("gitlab-dashboard.tools.internal", PageZoom.Half)))

        viewModel.onPageStarted(arte)
        assertThat(viewModel.pageZoom.value).isEqualTo(PageZoom.Full)

        viewModel.onPageStarted(dashboard)
        assertThat(viewModel.pageZoom.value).isEqualTo(PageZoom.Half)
    }

    @Test
    fun `Given a redirect to another host When the page starts Then the zoom of the new host applies`() {
        val viewModel = viewModel(arte, FakePageZoom(SiteZoom("arte.tv", PageZoom.Half)))
        assertThat(viewModel.pageZoom.value).isEqualTo(PageZoom.Full)

        viewModel.onPageStarted("https://arte.tv/fr/")

        assertThat(viewModel.pageZoom.value).isEqualTo(PageZoom.Half)
    }

    @Test
    fun `Given a site without its own zoom When the default level changes in Settings Then the page follows`() {
        val viewModel = viewModel(arte)

        defaultZoom.value = PageZoom.ThreeQuarters

        assertThat(viewModel.pageZoom.value).isEqualTo(PageZoom.ThreeQuarters)
    }

    @Test
    fun `Given a site with its own zoom When the default level changes in Settings Then the site keeps its zoom`() {
        val viewModel = viewModel(dashboard, FakePageZoom(SiteZoom("gitlab-dashboard.tools.internal", PageZoom.Half)))

        defaultZoom.value = PageZoom.ThreeQuarters

        assertThat(viewModel.pageZoom.value).isEqualTo(PageZoom.Half)
    }

    @Test
    fun `Given the dashboard at 100 percent When the user picks 50 percent Then it applies at once is remembered and announced`() = runTest {
        val zooms = FakePageZoom()
        val viewModel = viewModel(dashboard, zooms)

        viewModel.selectPageZoom(PageZoom.Half)

        assertThat(viewModel.pageZoom.value).isEqualTo(PageZoom.Half)
        assertThat(zooms.current).containsExactly(SiteZoom("gitlab-dashboard.tools.internal", PageZoom.Half))
        assertThat(viewModel.notice.value)
            .isEqualTo(BrowserNotice.ZoomSaved(PageZoom.Half, "gitlab-dashboard.tools.internal"))
    }

    @Test
    fun `Given the menu is open When the user picks a zoom Then the menu closes to show the page and the message`() = runTest {
        val viewModel = viewModel(dashboard)
        viewModel.openMenu()

        viewModel.selectPageZoom(PageZoom.Half)

        assertThat(viewModel.layers.value.isMenuOpen).isFalse()
        assertThat(viewModel.notice.value)
            .isEqualTo(BrowserNotice.ZoomSaved(PageZoom.Half, "gitlab-dashboard.tools.internal"))
    }

    @Test
    fun `Given a zoom change When it applies Then the page is not reloaded`() = runTest {
        val viewModel = viewModel(arte)
        viewModel.onWebViewReady()

        viewModel.selectPageZoom(PageZoom.Half)

        viewModel.commands.test {
            assertThat(awaitItem()).isEqualTo(BrowserCommand.Load(arte))
            expectNoEvents()
        }
    }

    @Test
    fun `Given a site zoomed to 50 percent When the user picks the default level Then the site zoom is removed`() = runTest {
        val zooms = FakePageZoom(SiteZoom("gitlab-dashboard.tools.internal", PageZoom.Half))
        val viewModel = viewModel(dashboard, zooms)

        viewModel.selectPageZoom(PageZoom.Full)

        assertThat(zooms.current).isEmpty()
        assertThat(viewModel.pageZoom.value).isEqualTo(PageZoom.Full)
        assertThat(viewModel.notice.value)
            .isEqualTo(BrowserNotice.ZoomReset(PageZoom.Full, "gitlab-dashboard.tools.internal"))
    }

    @Test
    fun `Given the default level is 75 percent When the user picks 75 for a zoomed site Then it follows the default instead of a stored copy`() = runTest {
        defaultZoom.value = PageZoom.ThreeQuarters
        val zooms = FakePageZoom(SiteZoom("arte.tv", PageZoom.Half))
        val viewModel = viewModel("https://arte.tv/", zooms)

        viewModel.selectPageZoom(PageZoom.ThreeQuarters)

        assertThat(zooms.current).isEmpty()
        assertThat(viewModel.pageZoom.value).isEqualTo(PageZoom.ThreeQuarters)
    }

    @Test
    fun `Given the storage fails When the user picks a zoom Then a notice says it was not saved and no success is announced`() = runTest {
        val zooms = FakePageZoom().apply { failure = "disk full" }
        val viewModel = viewModel(dashboard, zooms)

        viewModel.selectPageZoom(PageZoom.Half)

        assertThat(viewModel.notice.value).isEqualTo(BrowserNotice.SettingFailed)
        assertThat(viewModel.pageZoom.value).isEqualTo(PageZoom.Full)
    }

    @Test
    fun `Given a page without a readable host When the user picks a zoom Then nothing is stored and a notice is raised`() = runTest {
        val zooms = FakePageZoom()
        val viewModel = viewModel("about:blank", zooms)

        viewModel.selectPageZoom(PageZoom.Half)

        assertThat(zooms.current).isEmpty()
        assertThat(viewModel.notice.value).isEqualTo(BrowserNotice.SettingFailed)
    }
}

package com.openctv.browser.features.browser

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.history.PageVisitsContract
import com.openctv.browser.core.webview.LoadErrorCause
import com.openctv.browser.core.webview.SecurityWarning
import com.openctv.browser.core.webview.WebLoadError
import com.openctv.browser.features.browser.domain.BackAction
import com.openctv.browser.features.browser.domain.BrowserLayers
import com.openctv.browser.features.browser.domain.BrowserMode
import com.openctv.browser.features.browser.domain.PageState
import com.openctv.browser.features.browser.domain.backAction
import com.openctv.browser.features.browser.ui.BrowserCommand
import com.openctv.browser.features.browser.ui.BrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Historique côté navigateur : ce qui est enregistré, la surcouche Historique et la réouverture d'une entrée. */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserHistoryTest {

    private val url = "https://www.arte.tv/fr/"
    private val bookmarks = FakeBookmarks()
    private val visits = RecordingVisits()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(securityExceptions: FakeSecurityExceptions = FakeSecurityExceptions()) =
        BrowserViewModel(url, bookmarks, securityExceptions = securityExceptions, pageVisits = visits)

    @Test
    fun `Given a page finishes loading with its title When it is displayed Then it is recorded once with that title`() = runTest {
        val viewModel = viewModel()

        viewModel.onPageStarted(url)
        viewModel.onTitleChanged("ARTE")
        viewModel.onPageFinished(url)

        assertThat(visits.recorded).containsExactly(url to "ARTE")
    }

    @Test
    fun `Given a page finished before its title When the title arrives Then the visit is completed with the title`() = runTest {
        val viewModel = viewModel()

        viewModel.onPageStarted(url)
        viewModel.onPageFinished(url)
        viewModel.onTitleChanged("ARTE")

        assertThat(visits.recorded).containsExactly(url to "", url to "ARTE").inOrder()
    }

    @Test
    fun `Given a page still loading When its title arrives Then nothing is recorded before it is displayed`() = runTest {
        val viewModel = viewModel()

        viewModel.onPageStarted(url)
        viewModel.onTitleChanged("ARTE")

        assertThat(visits.recorded).isEmpty()
    }

    @Test
    fun `Given a single-page site already displayed When it changes its address without reloading Then the new address is recorded`() = runTest {
        val viewModel = viewModel()
        val video = "https://www.youtube.com/watch?v=abc"
        viewModel.onPageStarted("https://www.youtube.com/")
        viewModel.onUrlChanged("https://www.youtube.com/", canGoBack = false, canGoForward = false)
        viewModel.onTitleChanged("YouTube")
        viewModel.onPageFinished("https://www.youtube.com/")

        viewModel.onUrlChanged(video, canGoBack = true, canGoForward = false)
        viewModel.onTitleChanged("Une vidéo - YouTube")

        assertThat(visits.recorded).containsExactly(
            "https://www.youtube.com/" to "YouTube",
            video to "YouTube",
            video to "Une vidéo - YouTube",
        ).inOrder()
    }

    @Test
    fun `Given a page still loading When its address is committed Then it is recorded only once it is displayed`() = runTest {
        val viewModel = viewModel()
        viewModel.onPageStarted(url)

        viewModel.onUrlChanged(url, canGoBack = false, canGoForward = false)
        viewModel.onPageFinished(url)
        viewModel.onUrlChanged(url, canGoBack = false, canGoForward = false)

        assertThat(visits.recorded).containsExactly(url to "")
    }

    @Test
    fun `Given a security warning is shown When the engine reports an address change Then nothing is recorded`() = runTest {
        val viewModel = BrowserViewModel("http://nas.maison/", bookmarks, pageVisits = visits)
        viewModel.onWebViewReady()

        viewModel.onUrlChanged("http://nas.maison/", canGoBack = false, canGoForward = false)

        assertThat(visits.recorded).isEmpty()
    }

    @Test
    fun `Given a load error When WebView finishes its own error page Then nothing is recorded`() = runTest {
        val viewModel = viewModel()
        viewModel.onPageStarted(url)

        viewModel.onLoadError(WebLoadError(url, LoadErrorCause.Unreachable))
        viewModel.onPageFinished("data:text/html,error")
        viewModel.onTitleChanged("Webpage not available")

        assertThat(visits.recorded).isEmpty()
    }

    @Test
    fun `Given an HTTP page without exception When the user goes back to safety Then the warning is never recorded`() = runTest {
        val viewModel = BrowserViewModel("http://nas.maison/", bookmarks, pageVisits = visits)
        viewModel.onWebViewReady()
        assertThat((viewModel.pageState.value as PageState.Error).warning).isInstanceOf(SecurityWarning.InsecureHttp::class.java)

        viewModel.onPageFinished("http://nas.maison/")
        viewModel.returnToSafety(canGoBack = false)

        assertThat(visits.recorded).isEmpty()
    }

    @Test
    fun `Given a security warning is shown When the engine finishes the page under it Then the warning page is not recorded`() = runTest {
        val viewModel = viewModel()
        viewModel.onPageStarted(url)
        viewModel.onLoadError(WebLoadError("https://self-signed.example/", LoadErrorCause.Unknown))

        viewModel.onPageFinished("https://self-signed.example/")

        assertThat(visits.recorded).isEmpty()
    }

    @Test
    fun `Given the menu is open When the user opens the history Then the menu closes and the history is the top layer`() = runTest {
        val viewModel = viewModel()
        viewModel.enterInterfaceMode()
        viewModel.openMenu()

        viewModel.openHistory()

        val layers = viewModel.layers.value
        assertThat(layers.isHistoryOpen).isTrue()
        assertThat(layers.isMenuOpen).isFalse()
        assertThat(layers.backAction(canGoBack = true)).isEqualTo(BackAction.CloseHistory)
    }

    @Test
    fun `Given the history is open When Back is pressed Then it closes and the page is untouched`() = runTest {
        val viewModel = viewModel()
        viewModel.onWebViewReady()
        viewModel.openHistory()

        viewModel.onBack(canGoBack = true)

        assertThat(viewModel.layers.value.isHistoryOpen).isFalse()
        viewModel.commands.test {
            assertThat(awaitItem()).isEqualTo(BrowserCommand.Load(url))
            expectNoEvents()
        }
    }

    @Test
    fun `Given a video in fullscreen When the history is requested Then it stays closed`() = runTest {
        val viewModel = viewModel()
        viewModel.onFullscreenShown()

        viewModel.openHistory()

        assertThat(viewModel.layers.value.isHistoryOpen).isFalse()
    }

    @Test
    fun `Given the history is open When the user picks an https page Then it loads in the browser and the history closes`() = runTest {
        val viewModel = viewModel()
        viewModel.enterInterfaceMode()
        viewModel.openHistory()

        viewModel.onHistoryEntryChosen("https://france.tv/")

        assertThat(viewModel.layers.value.isHistoryOpen).isFalse()
        assertThat(viewModel.layers.value.mode).isEqualTo(BrowserMode.Page)
        viewModel.commands.test { assertThat(awaitItem()).isEqualTo(BrowserCommand.Load("https://france.tv/")) }
    }

    @Test
    fun `Given the history is open When the user picks an HTTP page without exception Then the warning shows instead of the page`() = runTest {
        val viewModel = viewModel()
        viewModel.openHistory()

        viewModel.onHistoryEntryChosen("http://nas.maison/")

        val state = viewModel.pageState.value as PageState.Error
        assertThat(state.warning).isInstanceOf(SecurityWarning.InsecureHttp::class.java)
        viewModel.commands.test { expectNoEvents() }
    }

    @Test
    fun `Given the history above every other layer When Back is pressed Then the history closes first`() {
        val layers = BrowserLayers(isMenuOpen = true, mode = BrowserMode.Interface, isHistoryOpen = true)

        assertThat(layers.backAction(canGoBack = true)).isEqualTo(BackAction.CloseHistory)
        assertThat(layers.isOverlayOpen).isTrue()
    }
}

private class RecordingVisits : PageVisitsContract {
    val recorded = mutableListOf<Pair<String, String>>()

    override suspend fun recordVisit(url: String, title: String): Resource<Unit> {
        recorded += url to title
        return Resource.Success(Unit)
    }
}

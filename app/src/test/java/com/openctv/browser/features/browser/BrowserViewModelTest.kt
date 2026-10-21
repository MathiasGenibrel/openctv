package com.openctv.browser.features.browser

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.webview.LoadErrorCause
import com.openctv.browser.core.webview.WebLoadError
import com.openctv.browser.features.browser.domain.BrowserMode
import com.openctv.browser.features.browser.domain.BrowserNotice
import com.openctv.browser.features.browser.domain.MenuWriteFailure
import com.openctv.browser.features.browser.domain.PageState
import com.openctv.browser.features.browser.domain.ReloadAction
import com.openctv.browser.features.browser.domain.siteLabelOf
import com.openctv.browser.features.browser.ui.BrowserCommand
import com.openctv.browser.features.browser.ui.BrowserViewModel
import com.openctv.browser.features.search.domain.SearchMode
import com.openctv.browser.features.search.domain.SearchRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {

    private val url = "https://www.arte.tv/fr/"
    private val bookmarks = FakeBookmarks()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `Given a web address When the screen opens Then it loads that page and shows the loading state`() = runTest {
        val viewModel = BrowserViewModel(url, bookmarks)
        viewModel.onWebViewReady()

        assertThat(viewModel.pageState.value).isEqualTo(PageState.Loading("arte.tv", 0f))
        viewModel.commands.test {
            assertThat(awaitItem()).isEqualTo(BrowserCommand.Load(url))
        }
    }

    @Test
    fun `Given a page loading When progress advances and the page finishes Then the state goes loading to loaded`() {
        val viewModel = BrowserViewModel(url, bookmarks)

        viewModel.onPageStarted(url)
        viewModel.onProgressChanged(40)
        assertThat(viewModel.pageState.value).isEqualTo(PageState.Loading("arte.tv", 0.4f))

        viewModel.onPageFinished(url)
        assertThat(viewModel.pageState.value).isEqualTo(PageState.Loaded("arte.tv"))
    }

    @Test
    fun `Given the TV is offline When the page fails Then the error names the site and WebView finishing does not hide it`() {
        val viewModel = BrowserViewModel(url, bookmarks)
        viewModel.onPageStarted(url)

        viewModel.onLoadError(WebLoadError(url, LoadErrorCause.Unreachable))
        viewModel.onPageFinished("data:text/html,error")

        assertThat(viewModel.pageState.value)
            .isEqualTo(PageState.Error("arte.tv", LoadErrorCause.Unreachable))
    }

    @Test
    fun `Given an error screen When the user retries Then the same page is loaded again`() = runTest {
        val viewModel = BrowserViewModel(url, bookmarks)
        viewModel.onWebViewReady()
        viewModel.onPageStarted(url)
        viewModel.onLoadError(WebLoadError(url, LoadErrorCause.Unreachable))

        viewModel.commands.test {
            awaitItem() // chargement initial
            viewModel.retry()

            assertThat(viewModel.pageState.value).isEqualTo(PageState.Loading("arte.tv", 0f))
            assertThat(awaitItem()).isEqualTo(BrowserCommand.Load(url))
        }
    }

    @Test
    fun `Given a certificate error When the page fails Then the cause is the invalid certificate`() {
        val viewModel = BrowserViewModel("https://expired.badssl.com", bookmarks)

        viewModel.onLoadError(WebLoadError("https://expired.badssl.com/", LoadErrorCause.InvalidCertificate))

        assertThat(viewModel.pageState.value)
            .isEqualTo(PageState.Error("expired.badssl.com", LoadErrorCause.InvalidCertificate))
    }

    @Test
    fun `Given a non web address When the screen opens Then nothing is loaded and an error is shown`() = runTest {
        val viewModel = BrowserViewModel("intent://evil", bookmarks)
        viewModel.onWebViewReady()

        assertThat(viewModel.pageState.value).isInstanceOf(PageState.Error::class.java)
        viewModel.commands.test { expectNoEvents() }
    }

    @Test
    fun `Given a non web address refused at opening When the user retries Then it is still never loaded`() = runTest {
        val viewModel = BrowserViewModel("javascript:alert(1)", bookmarks)
        viewModel.onWebViewReady()

        viewModel.retry()

        assertThat(viewModel.pageState.value).isInstanceOf(PageState.Error::class.java)
        viewModel.commands.test { expectNoEvents() }
    }

    @Test
    fun `Given a page on screen When the user follows a link Then the page stays visible under the progress bar`() {
        val viewModel = BrowserViewModel(url, bookmarks)
        viewModel.onPageStarted(url)
        viewModel.onPageFinished(url)

        viewModel.onPageStarted("https://www.arte.tv/fr/videos/")
        viewModel.onProgressChanged(30)

        assertThat(viewModel.pageState.value)
            .isEqualTo(PageState.Loading("arte.tv", 0.3f, isPageShown = true))
    }

    @Test
    fun `Given nothing displayed yet or an error When a page starts loading Then the full loading screen is shown`() {
        val viewModel = BrowserViewModel(url, bookmarks)

        viewModel.onPageStarted(url)
        assertThat((viewModel.pageState.value as PageState.Loading).isPageShown).isFalse()

        viewModel.onLoadError(WebLoadError(url, LoadErrorCause.Unreachable))
        viewModel.onPageStarted(url)
        assertThat((viewModel.pageState.value as PageState.Loading).isPageShown).isFalse()
    }

    @Test
    fun `Given the user browsed further When a fresh WebView replaces the old one Then the current page is reloaded`() =
        runTest {
            val viewModel = BrowserViewModel(url, bookmarks)
            val visited = "https://www.arte.tv/fr/videos/"
            viewModel.onPageStarted(visited)
            viewModel.onPageFinished(visited)

            viewModel.onWebViewReady()

            assertThat(viewModel.pageState.value).isEqualTo(PageState.Loading("arte.tv", 0f))
            viewModel.commands.test {
                assertThat(awaitItem()).isEqualTo(BrowserCommand.Load(visited))
            }
        }

    @Test
    fun `Given a page with history When the user presses Back Then the browser goes back in the page history`() =
        runTest {
            val viewModel = BrowserViewModel(url, bookmarks)

            viewModel.onBack(canGoBack = true)

            viewModel.commands.test {
                assertThat(awaitItem()).isEqualTo(BrowserCommand.GoBack)
            }
        }

    @Test
    fun `Given a certificate error over a page of the history When the user presses Back Then the previous page is shown again without going back`() =
        runTest {
            val results = "https://duckduckgo.com/?q=badssl"
            val expired = "https://expired.badssl.com/"
            val viewModel = BrowserViewModel(results, bookmarks)
            viewModel.onUrlChanged(results, canGoBack = true, canGoForward = false)
            viewModel.onPageFinished(results)
            viewModel.onPageStarted(expired)
            // Certificat refusé : la page expirée n'entre jamais dans l'historique, la WebView garde les résultats.
            viewModel.onLoadError(WebLoadError(expired, LoadErrorCause.InvalidCertificate))

            viewModel.onBack(canGoBack = true)

            assertThat(viewModel.pageState.value).isEqualTo(PageState.Loaded("duckduckgo.com"))
            assertThat(viewModel.pageUrl.value).isEqualTo(results)
            viewModel.commands.test { expectNoEvents() }
        }

    @Test
    fun `Given a link to a page with a refused certificate When the user presses Back Then the page that held the link is shown again`() =
        runTest {
            val results = "https://duckduckgo.com/?q=badssl"
            val viewModel = BrowserViewModel(results, bookmarks)
            viewModel.onUrlChanged(results, canGoBack = true, canGoForward = false)
            viewModel.onPageFinished(results)
            // Lien suivi : la WebView signale l'erreur sans jamais avoir commencé la page en échec.
            viewModel.onLoadError(WebLoadError("https://expired.badssl.com/", LoadErrorCause.InvalidCertificate))

            viewModel.onBack(canGoBack = true)

            assertThat(viewModel.pageState.value).isEqualTo(PageState.Loaded("duckduckgo.com"))
            viewModel.commands.test { expectNoEvents() }
        }

    @Test
    fun `Given an error whose page is in the WebView history When the user presses Back Then the browser goes back once`() =
        runTest {
            val viewModel = BrowserViewModel(url, bookmarks)
            viewModel.onPageStarted(url)
            viewModel.onLoadError(WebLoadError(url, LoadErrorCause.Unreachable))
            // La WebView a ajouté sa page d'erreur à l'historique.
            viewModel.onUrlChanged(url, canGoBack = true, canGoForward = false)

            viewModel.onBack(canGoBack = true)

            viewModel.commands.test { assertThat(awaitItem()).isEqualTo(BrowserCommand.GoBack) }
        }

    @Test
    fun `Given a certificate error on the very first page When the user presses Back Then the browser returns home`() = runTest {
        val viewModel = BrowserViewModel(url, bookmarks)
        viewModel.onPageStarted(url)
        viewModel.onLoadError(WebLoadError(url, LoadErrorCause.InvalidCertificate))

        viewModel.onBack(canGoBack = false)

        viewModel.commands.test { assertThat(awaitItem()).isEqualTo(BrowserCommand.Close) }
    }

    @Test
    fun `Given the first page of the visit When the user presses Back Then the browser returns home`() = runTest {
        val viewModel = BrowserViewModel(url, bookmarks)

        viewModel.onBack(canGoBack = false)

        viewModel.commands.test {
            assertThat(awaitItem()).isEqualTo(BrowserCommand.Close)
        }
    }

    @Test
    fun `Given various addresses When the site is displayed Then only the readable host remains`() {
        assertThat(siteLabelOf("https://www.arte.tv/fr/?x=1")).isEqualTo("arte.tv")
        assertThat(siteLabelOf("http://user@example.org:8080/path")).isEqualTo("example.org")
        assertThat(siteLabelOf("https://news.ycombinator.com#top")).isEqualTo("news.ycombinator.com")
    }

    @Test
    fun `Given pages loading When one finishes and another fails Then only the loaded page is recorded as visited`() {
        val visited = mutableListOf<String>()
        val viewModel = BrowserViewModel(url, bookmarks, onPageVisited = { visited += it })

        viewModel.onPageStarted(url)
        viewModel.onPageFinished(url)
        viewModel.onPageStarted("https://down.example")
        viewModel.onLoadError(WebLoadError("https://down.example", LoadErrorCause.Unreachable))
        viewModel.onPageFinished("data:text/html,error")

        assertThat(visited).containsExactly(url)
    }

    private fun pageOnScreen(): BrowserViewModel {
        val viewModel = BrowserViewModel(url, bookmarks)
        val visited = "https://www.arte.tv/fr/videos/"
        viewModel.onPageStarted(visited)
        viewModel.onPageFinished(visited)
        return viewModel
    }

    @Test
    fun `Given a page on screen When the user opens the address input Then it opens prefilled with the page address`() {
        val viewModel = pageOnScreen()

        viewModel.openAddressInput()

        assertThat(viewModel.layers.value.addressInput)
            .isEqualTo(SearchRequest(SearchMode.Browse, initialText = "https://www.arte.tv/fr/videos/"))
    }

    @Test
    fun `Given the address input open When the user presses Back Then it closes and the page is neither left nor reloaded`() =
        runTest {
            val viewModel = pageOnScreen()
            viewModel.openAddressInput()

            viewModel.commands.test {
                viewModel.onBack(canGoBack = true)

                assertThat(viewModel.layers.value.addressInput).isNull()
                assertThat(viewModel.pageState.value).isEqualTo(PageState.Loaded("arte.tv"))
                expectNoEvents()
            }
        }

    @Test
    fun `Given the address input closed again When the user presses Back Then the page history goes back as before`() =
        runTest {
            val viewModel = pageOnScreen()
            viewModel.openAddressInput()
            viewModel.closeAddressInput()

            viewModel.commands.test {
                viewModel.onBack(canGoBack = true)

                assertThat(awaitItem()).isEqualTo(BrowserCommand.GoBack)
            }
        }

    @Test
    fun `Given the address input open When the user validates an address Then it closes and loads in the same page`() =
        runTest {
            val viewModel = pageOnScreen()
            viewModel.openAddressInput()

            viewModel.commands.test {
                viewModel.onAddressChosen("https://example.org")

                assertThat(viewModel.layers.value.addressInput).isNull()
                assertThat(awaitItem()).isEqualTo(BrowserCommand.Load("https://example.org"))
                expectNoEvents()
            }
        }

    // --- Retour : saisie -> menu -> mode interface -> historique -> accueil ---

    @Test
    fun `Given the menu open When the user presses Back Then only the menu closes`() = runTest {
        val viewModel = pageOnScreen()
        viewModel.enterInterfaceMode()
        viewModel.openMenu()

        viewModel.commands.test {
            viewModel.onBack(canGoBack = true)

            assertThat(viewModel.layers.value.isMenuOpen).isFalse()
            assertThat(viewModel.layers.value.mode).isEqualTo(BrowserMode.Interface)
            expectNoEvents()
        }
    }

    @Test
    fun `Given the interface mode When the user presses Back Then the page gets the D-pad back and history is untouched`() =
        runTest {
            val viewModel = pageOnScreen()
            viewModel.enterInterfaceMode()

            viewModel.commands.test {
                viewModel.onBack(canGoBack = true)

                assertThat(viewModel.layers.value.mode).isEqualTo(BrowserMode.Page)
                expectNoEvents()
            }
        }

    @Test
    fun `Given the address input opened from the interface When the user presses Back twice Then the input closes then the interface`() {
        val viewModel = pageOnScreen()
        viewModel.enterInterfaceMode()
        viewModel.openAddressInput()

        viewModel.onBack(canGoBack = true)
        assertThat(viewModel.layers.value.addressInput).isNull()
        assertThat(viewModel.layers.value.mode).isEqualTo(BrowserMode.Interface)

        viewModel.onBack(canGoBack = true)
        assertThat(viewModel.layers.value.mode).isEqualTo(BrowserMode.Page)
    }

    @Test
    fun `Given the page mode and no history When the user presses Back Then the app goes home and never exits`() = runTest {
        val viewModel = pageOnScreen()

        viewModel.onBack(canGoBack = false)

        viewModel.commands.test {
            assertThat(awaitItem()).isEqualTo(BrowserCommand.Close)
        }
    }

    // --- Mode interface ---

    @Test
    fun `Given the top of a page When the user presses Up Then the interface mode opens and the key is consumed`() {
        val viewModel = pageOnScreen()

        val isConsumed = viewModel.onDpadUp(canScrollUp = false)

        assertThat(isConsumed).isTrue()
        assertThat(viewModel.layers.value.mode).isEqualTo(BrowserMode.Interface)
    }

    @Test
    fun `Given a page that can still scroll up When the user presses Up Then the page scrolls and the mode is unchanged`() {
        val viewModel = pageOnScreen()

        val isConsumed = viewModel.onDpadUp(canScrollUp = true)

        assertThat(isConsumed).isFalse()
        assertThat(viewModel.layers.value.mode).isEqualTo(BrowserMode.Page)
    }

    @Test
    fun `Given the interface mode When the user presses Down Then the page gets the D-pad back`() {
        val viewModel = pageOnScreen()
        viewModel.enterInterfaceMode()

        val isConsumed = viewModel.onDpadDown()

        assertThat(isConsumed).isTrue()
        assertThat(viewModel.layers.value.mode).isEqualTo(BrowserMode.Page)
    }

    @Test
    fun `Given the address input open When the user presses Down Then the key is left to the input suggestions`() {
        val viewModel = pageOnScreen()
        viewModel.enterInterfaceMode()
        viewModel.openAddressInput()

        assertThat(viewModel.onDpadDown()).isFalse()
        assertThat(viewModel.layers.value.mode).isEqualTo(BrowserMode.Interface)
    }

    @Test
    fun `Given the page mode When the user presses the remote Menu key Then the interface mode opens`() {
        val viewModel = pageOnScreen()

        assertThat(viewModel.onMenuKey()).isTrue()
        assertThat(viewModel.layers.value.mode).isEqualTo(BrowserMode.Interface)
    }

    @Test
    fun `Given the interface mode When the user validates an address Then the page is loaded and the page mode returns`() {
        val viewModel = pageOnScreen()
        viewModel.enterInterfaceMode()
        viewModel.openAddressInput()

        viewModel.onAddressChosen("https://example.org")

        assertThat(viewModel.layers.value.mode).isEqualTo(BrowserMode.Page)
    }

    // --- Barre d'outils ---

    @Test
    fun `Given a page loading When the toolbar is read Then Refresh becomes Stop and returns to Refresh once loaded`() =
        runTest {
            val viewModel = BrowserViewModel(url, bookmarks)

            viewModel.toolbar.test {
                viewModel.onPageStarted(url)
                assertThat(expectMostRecentItem().reloadAction).isEqualTo(ReloadAction.Stop)

                viewModel.onPageFinished(url)
                assertThat(expectMostRecentItem().reloadAction).isEqualTo(ReloadAction.Reload)
            }
        }

    @Test
    fun `Given a page loading When the user presses Stop Then the loading is stopped and the page is settled`() = runTest {
        val viewModel = BrowserViewModel(url, bookmarks)
        viewModel.onPageStarted(url)

        viewModel.commands.test {
            viewModel.onReloadOrStop()

            assertThat(awaitItem()).isEqualTo(BrowserCommand.Stop)
            assertThat(viewModel.pageState.value).isEqualTo(PageState.Loaded("arte.tv"))
        }
    }

    @Test
    fun `Given a loaded page When the user presses Refresh Then the same WebView reloads without a full loading screen`() =
        runTest {
            val viewModel = pageOnScreen()

            viewModel.commands.test {
                viewModel.onReloadOrStop()
                assertThat(awaitItem()).isEqualTo(BrowserCommand.Reload)

                // WebView annonce ensuite le démarrage : la page reste visible sous la barre de progression.
                viewModel.onPageStarted("https://www.arte.tv/fr/videos/")
                assertThat((viewModel.pageState.value as PageState.Loading).isPageShown).isTrue()
            }
        }

    @Test
    fun `Given an error screen When the user presses Refresh Then the address is loaded again`() = runTest {
        val viewModel = BrowserViewModel(url, bookmarks)
        viewModel.onPageStarted(url)
        viewModel.onLoadError(WebLoadError(url, LoadErrorCause.Unreachable))

        viewModel.commands.test {
            viewModel.onReloadOrStop()

            assertThat(awaitItem()).isEqualTo(BrowserCommand.Load(url))
        }
    }

    @Test
    fun `Given no history When the toolbar is read Then Back and Forward are disabled and commands are ignored`() = runTest {
        val viewModel = pageOnScreen()

        viewModel.toolbar.test {
            val state = expectMostRecentItem()
            assertThat(state.canGoBack).isFalse()
            assertThat(state.canGoForward).isFalse()
        }
        viewModel.commands.test {
            viewModel.goBack()
            viewModel.goForward()
            expectNoEvents()
        }
    }

    @Test
    fun `Given a single page app navigating in place When the URL changes Then the toolbar follows address and history`() =
        runTest {
            val viewModel = pageOnScreen()

            viewModel.toolbar.test {
                viewModel.onUrlChanged("https://m.youtube.com/watch?v=1", canGoBack = true, canGoForward = false)

                val state = expectMostRecentItem()
                assertThat(state.site).isEqualTo("m.youtube.com")
                assertThat(state.canGoBack).isTrue()
                assertThat(state.isSecure).isTrue()
            }
            viewModel.commands.test {
                viewModel.goBack()
                assertThat(awaitItem()).isEqualTo(BrowserCommand.GoBack)
            }
        }

    @Test
    fun `Given a page over plain http When the toolbar is read Then no padlock is shown`() = runTest {
        val viewModel = BrowserViewModel("http://example.org", bookmarks)

        viewModel.toolbar.test {
            assertThat(expectMostRecentItem().isSecure).isFalse()
        }
    }

    @Test
    fun `Given a certificate error on a site When the error screen shows Then the bar shows no padlock for the refused address`() =
        runTest {
            val viewModel = pageOnScreen()
            viewModel.onAddressChosen("https://expired.badssl.com")

            viewModel.onLoadError(WebLoadError("https://expired.badssl.com/", LoadErrorCause.InvalidCertificate))

            assertThat(viewModel.pageState.value)
                .isEqualTo(PageState.Error("expired.badssl.com", LoadErrorCause.InvalidCertificate))
            viewModel.toolbar.test {
                val state = expectMostRecentItem()
                assertThat(state.site).isEqualTo("expired.badssl.com")
                assertThat(state.isSecure).isFalse()
            }
        }

    @Test
    fun `Given wikipedia org is a favorite When the site redirects to www wikipedia org Then the star is filled`() =
        runTest {
            bookmarks.seed("https://wikipedia.org")
            val viewModel = BrowserViewModel("https://wikipedia.org", bookmarks)

            viewModel.onUrlChanged("https://www.wikipedia.org/", canGoBack = false, canGoForward = false)

            viewModel.toolbar.test { assertThat(expectMostRecentItem().isBookmarked).isTrue() }
        }

    @Test
    fun `Given a page that is not a favorite When the user toggles the favorite Then it is added then removed`() = runTest {
        val viewModel = pageOnScreen()

        viewModel.toolbar.test {
            assertThat(expectMostRecentItem().isBookmarked).isFalse()

            viewModel.toggleBookmark()
            assertThat(expectMostRecentItem().isBookmarked).isTrue()
            assertThat(bookmarks.urls).hasSize(1)

            viewModel.toggleBookmark()
            assertThat(expectMostRecentItem().isBookmarked).isFalse()
            assertThat(bookmarks.urls).isEmpty()
        }
    }

    @Test
    fun `Given a favorite page When the user navigates elsewhere Then the star is empty again`() = runTest {
        bookmarks.seed("https://www.arte.tv/fr/videos/")
        val viewModel = pageOnScreen()

        viewModel.toolbar.test {
            assertThat(expectMostRecentItem().isBookmarked).isTrue()

            viewModel.onPageStarted("https://example.org")
            assertThat(expectMostRecentItem().isBookmarked).isFalse()
        }
    }

    @Test
    fun `Given the interface mode When the user opens the menu Then the menu state is open until closed`() {
        val viewModel = pageOnScreen()

        viewModel.openMenu()
        assertThat(viewModel.layers.value.isMenuOpen).isTrue()

        viewModel.closeMenu()
        assertThat(viewModel.layers.value.isMenuOpen).isFalse()
    }

    @Test
    fun `Given the storage fails When the user toggles the favorite Then a brief notice explains it and the star stays empty`() =
        runTest {
            bookmarks.failure = "disk full"
            val viewModel = pageOnScreen()

            viewModel.toolbar.test {
                viewModel.toggleBookmark()

                assertThat(viewModel.notice.value).isEqualTo(BrowserNotice.BookmarkFailed)
                assertThat(expectMostRecentItem().isBookmarked).isFalse()
            }
            advanceTimeBy(BrowserViewModel.NOTICE_DURATION)
            runCurrent()
            assertThat(viewModel.notice.value).isNull()
        }

    @Test
    fun `Given a page When a video goes fullscreen and the player hides it Then the fullscreen layer follows`() {
        val viewModel = BrowserViewModel(url, bookmarks)

        viewModel.onFullscreenShown()
        assertThat(viewModel.layers.value.isFullscreen).isTrue()

        viewModel.onFullscreenHidden()
        assertThat(viewModel.layers.value.isFullscreen).isFalse()
    }

    @Test
    fun `Given a video in fullscreen When the user presses Back Then the player is asked to exit and the browser stays`() = runTest {
        val viewModel = BrowserViewModel(url, bookmarks)
        viewModel.onFullscreenShown()

        viewModel.onBack(canGoBack = true)

        viewModel.commands.test {
            assertThat(awaitItem()).isEqualTo(BrowserCommand.ExitFullscreen)
            expectNoEvents()
        }
        assertThat(viewModel.layers.value.isFullscreen).isTrue()
    }

    @Test
    fun `Given a video in fullscreen When the user presses Up or Menu Then the keys are left to the player`() {
        val viewModel = BrowserViewModel(url, bookmarks)
        viewModel.onFullscreenShown()

        assertThat(viewModel.onDpadUp(canScrollUp = false)).isFalse()
        assertThat(viewModel.onMenuKey()).isFalse()
        assertThat(viewModel.layers.value.isInterfaceMode).isFalse()
    }

    @Test
    fun `Given the menu is open When the user chooses Accueil Then the browser asks to close`() = runTest {
        val viewModel = BrowserViewModel(url, bookmarks)
        viewModel.openMenu()

        viewModel.goHome()

        viewModel.commands.test { assertThat(awaitItem()).isEqualTo(BrowserCommand.Close) }
    }

    @Test
    fun `Given a video in fullscreen When the menu is requested Then it stays closed and Back still exits fullscreen`() = runTest {
        val viewModel = BrowserViewModel(url, bookmarks)
        viewModel.onFullscreenShown()

        viewModel.openMenu()
        viewModel.onBack(canGoBack = true)

        assertThat(viewModel.layers.value.isMenuOpen).isFalse()
        viewModel.commands.test { assertThat(awaitItem()).isEqualTo(BrowserCommand.ExitFullscreen) }
    }

    @Test
    fun `Given the menu could not save a setting When it reports the failure Then a brief notice explains it`() = runTest {
        val viewModel = BrowserViewModel(url, bookmarks)

        viewModel.onMenuWriteFailed(MenuWriteFailure.Setting)

        assertThat(viewModel.notice.value).isEqualTo(BrowserNotice.SettingFailed)
        advanceTimeBy(BrowserViewModel.NOTICE_DURATION)
        runCurrent()
        assertThat(viewModel.notice.value).isNull()
    }
}

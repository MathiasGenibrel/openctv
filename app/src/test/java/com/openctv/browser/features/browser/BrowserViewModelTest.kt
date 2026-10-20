package com.openctv.browser.features.browser

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.webview.LoadErrorCause
import com.openctv.browser.core.webview.WebLoadError
import com.openctv.browser.features.browser.domain.PageState
import com.openctv.browser.features.browser.domain.siteLabelOf
import com.openctv.browser.features.browser.ui.BrowserCommand
import com.openctv.browser.features.browser.ui.BrowserViewModel
import com.openctv.browser.features.search.domain.SearchMode
import com.openctv.browser.features.search.domain.SearchRequest
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BrowserViewModelTest {

    private val url = "https://www.arte.tv/fr/"

    @Test
    fun `Given a web address When the screen opens Then it loads that page and shows the loading state`() = runTest {
        val viewModel = BrowserViewModel(url)
        viewModel.onWebViewReady()

        assertThat(viewModel.pageState.value).isEqualTo(PageState.Loading("arte.tv", 0f))
        viewModel.commands.test {
            assertThat(awaitItem()).isEqualTo(BrowserCommand.Load(url))
        }
    }

    @Test
    fun `Given a page loading When progress advances and the page finishes Then the state goes loading to loaded`() {
        val viewModel = BrowserViewModel(url)

        viewModel.onPageStarted(url)
        viewModel.onProgressChanged(40)
        assertThat(viewModel.pageState.value).isEqualTo(PageState.Loading("arte.tv", 0.4f))

        viewModel.onPageFinished(url)
        assertThat(viewModel.pageState.value).isEqualTo(PageState.Loaded("arte.tv"))
    }

    @Test
    fun `Given the TV is offline When the page fails Then the error names the site and WebView finishing does not hide it`() {
        val viewModel = BrowserViewModel(url)
        viewModel.onPageStarted(url)

        viewModel.onLoadError(WebLoadError(url, LoadErrorCause.Unreachable))
        viewModel.onPageFinished("data:text/html,error")

        assertThat(viewModel.pageState.value)
            .isEqualTo(PageState.Error("arte.tv", LoadErrorCause.Unreachable))
    }

    @Test
    fun `Given an error screen When the user retries Then the same page is loaded again`() = runTest {
        val viewModel = BrowserViewModel(url)
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
        val viewModel = BrowserViewModel("https://expired.badssl.com")

        viewModel.onLoadError(WebLoadError("https://expired.badssl.com/", LoadErrorCause.InvalidCertificate))

        assertThat(viewModel.pageState.value)
            .isEqualTo(PageState.Error("expired.badssl.com", LoadErrorCause.InvalidCertificate))
    }

    @Test
    fun `Given a non web address When the screen opens Then nothing is loaded and an error is shown`() = runTest {
        val viewModel = BrowserViewModel("intent://evil")
        viewModel.onWebViewReady()

        assertThat(viewModel.pageState.value).isInstanceOf(PageState.Error::class.java)
        viewModel.commands.test { expectNoEvents() }
    }

    @Test
    fun `Given a non web address refused at opening When the user retries Then it is still never loaded`() = runTest {
        val viewModel = BrowserViewModel("javascript:alert(1)")
        viewModel.onWebViewReady()

        viewModel.retry()

        assertThat(viewModel.pageState.value).isInstanceOf(PageState.Error::class.java)
        viewModel.commands.test { expectNoEvents() }
    }

    @Test
    fun `Given a page on screen When the user follows a link Then the page stays visible under the progress bar`() {
        val viewModel = BrowserViewModel(url)
        viewModel.onPageStarted(url)
        viewModel.onPageFinished(url)

        viewModel.onPageStarted("https://www.arte.tv/fr/videos/")
        viewModel.onProgressChanged(30)

        assertThat(viewModel.pageState.value)
            .isEqualTo(PageState.Loading("arte.tv", 0.3f, isPageShown = true))
    }

    @Test
    fun `Given nothing displayed yet or an error When a page starts loading Then the full loading screen is shown`() {
        val viewModel = BrowserViewModel(url)

        viewModel.onPageStarted(url)
        assertThat((viewModel.pageState.value as PageState.Loading).isPageShown).isFalse()

        viewModel.onLoadError(WebLoadError(url, LoadErrorCause.Unreachable))
        viewModel.onPageStarted(url)
        assertThat((viewModel.pageState.value as PageState.Loading).isPageShown).isFalse()
    }

    @Test
    fun `Given the user browsed further When a fresh WebView replaces the old one Then the current page is reloaded`() =
        runTest {
            val viewModel = BrowserViewModel(url)
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
            val viewModel = BrowserViewModel(url)

            viewModel.onBack(canGoBack = true)

            viewModel.commands.test {
                assertThat(awaitItem()).isEqualTo(BrowserCommand.GoBack)
            }
        }

    @Test
    fun `Given the first page of the visit When the user presses Back Then the browser returns home`() = runTest {
        val viewModel = BrowserViewModel(url)

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
        val viewModel = BrowserViewModel(url, onPageVisited = { visited += it })

        viewModel.onPageStarted(url)
        viewModel.onPageFinished(url)
        viewModel.onPageStarted("https://down.example")
        viewModel.onLoadError(WebLoadError("https://down.example", LoadErrorCause.Unreachable))
        viewModel.onPageFinished("data:text/html,error")

        assertThat(visited).containsExactly(url)
    }

    private fun pageOnScreen(): BrowserViewModel {
        val viewModel = BrowserViewModel(url)
        val visited = "https://www.arte.tv/fr/videos/"
        viewModel.onPageStarted(visited)
        viewModel.onPageFinished(visited)
        return viewModel
    }

    @Test
    fun `Given a page on screen When the user opens the address input Then it opens prefilled with the page address`() {
        val viewModel = pageOnScreen()

        viewModel.openAddressInput()

        assertThat(viewModel.addressInput.value)
            .isEqualTo(SearchRequest(SearchMode.Browse, initialText = "https://www.arte.tv/fr/videos/"))
    }

    @Test
    fun `Given the address input open When the user presses Back Then it closes and the page is neither left nor reloaded`() =
        runTest {
            val viewModel = pageOnScreen()
            viewModel.openAddressInput()

            viewModel.commands.test {
                viewModel.onBack(canGoBack = true)

                assertThat(viewModel.addressInput.value).isNull()
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

                assertThat(viewModel.addressInput.value).isNull()
                assertThat(awaitItem()).isEqualTo(BrowserCommand.Load("https://example.org"))
                expectNoEvents()
            }
        }
}

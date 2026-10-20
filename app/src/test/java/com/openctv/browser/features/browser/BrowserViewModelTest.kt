package com.openctv.browser.features.browser

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.webview.LoadErrorCause
import com.openctv.browser.core.webview.WebLoadError
import com.openctv.browser.features.browser.domain.PageState
import com.openctv.browser.features.browser.domain.siteLabelOf
import com.openctv.browser.features.browser.ui.BrowserCommand
import com.openctv.browser.features.browser.ui.BrowserViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BrowserViewModelTest {

    private val url = "https://www.arte.tv/fr/"

    @Test
    fun `Given a web address When the screen opens Then it loads that page and shows the loading state`() = runTest {
        val viewModel = BrowserViewModel(url)

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

        assertThat(viewModel.pageState.value).isInstanceOf(PageState.Error::class.java)
        viewModel.commands.test { expectNoEvents() }
    }

    @Test
    fun `Given various addresses When the site is displayed Then only the readable host remains`() {
        assertThat(siteLabelOf("https://www.arte.tv/fr/?x=1")).isEqualTo("arte.tv")
        assertThat(siteLabelOf("http://user@example.org:8080/path")).isEqualTo("example.org")
        assertThat(siteLabelOf("https://news.ycombinator.com#top")).isEqualTo("news.ycombinator.com")
    }
}

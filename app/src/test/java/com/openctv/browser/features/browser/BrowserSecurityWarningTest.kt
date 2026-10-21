package com.openctv.browser.features.browser

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.security.SiteException
import com.openctv.browser.core.webview.CertificateProblem
import com.openctv.browser.core.webview.LoadErrorCause
import com.openctv.browser.core.webview.SecurityWarning
import com.openctv.browser.core.webview.WebLoadError
import com.openctv.browser.features.browser.domain.BackAction
import com.openctv.browser.features.browser.domain.BrowserLayers
import com.openctv.browser.features.browser.domain.BrowserNotice
import com.openctv.browser.features.browser.domain.PageInsecurity
import com.openctv.browser.features.browser.domain.PageState
import com.openctv.browser.features.browser.domain.backAction
import com.openctv.browser.features.browser.domain.browserHudOf
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

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserSecurityWarningTest {
    private val bookmarks = FakeBookmarks()
    private val httpUrl = "http://192.168.1.10/"
    private val expiredUrl = "https://nas.maison/"
    private val certificateWarning = SecurityWarning.InvalidCertificate(
        url = expiredUrl,
        host = "nas.maison",
        problem = CertificateProblem.Expired,
        issuedTo = "nas.maison",
        validUntil = null,
        fingerprint = "ab12",
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(url: String, exceptions: FakeSecurityExceptions = FakeSecurityExceptions()) =
        BrowserViewModel(url, bookmarks, securityExceptions = exceptions)

    @Test
    fun `Given an http address not allowed When the screen opens Then the warning shows and nothing is loaded`() = runTest {
        val viewModel = viewModel(httpUrl)
        viewModel.onWebViewReady()

        val state = viewModel.pageState.value as PageState.Error
        assertThat(state.warning).isEqualTo(SecurityWarning.InsecureHttp(httpUrl, "192.168.1.10"))
        viewModel.commands.test { expectNoEvents() }
    }

    @Test
    fun `Given an http host allowed When the screen opens Then the page loads without warning`() = runTest {
        val viewModel = viewModel(httpUrl, FakeSecurityExceptions(SiteException.Http("192.168.1.10")))
        viewModel.onWebViewReady()

        assertThat(viewModel.pageState.value).isInstanceOf(PageState.Loading::class.java)
        viewModel.commands.test { assertThat(awaitItem()).isEqualTo(BrowserCommand.Load(httpUrl)) }
    }

    @Test
    fun `Given an http warning When the user shows the page for the session only Then the host is allowed not remembered and the page loads`() = runTest {
        val exceptions = FakeSecurityExceptions()
        val viewModel = viewModel(httpUrl, exceptions)
        viewModel.onWebViewReady()

        viewModel.continueDespiteWarning(isRemembered = false)

        assertThat(exceptions.requests).containsExactly(SiteException.Http("192.168.1.10") to false)
        assertThat(viewModel.pageState.value).isInstanceOf(PageState.Loading::class.java)
        viewModel.commands.test { assertThat(awaitItem()).isEqualTo(BrowserCommand.Load(httpUrl)) }
    }

    @Test
    fun `Given an http warning When the user ticks the box and continues Then the exception is remembered on the TV`() = runTest {
        val exceptions = FakeSecurityExceptions()
        val viewModel = viewModel(httpUrl, exceptions)
        viewModel.onWebViewReady()

        viewModel.continueDespiteWarning(isRemembered = true)

        assertThat(exceptions.requests).containsExactly(SiteException.Http("192.168.1.10") to true)
    }

    @Test
    fun `Given a certificate warning When the user trusts the certificate Then the exception binds host and fingerprint and the page reloads`() = runTest {
        val exceptions = FakeSecurityExceptions()
        val viewModel = viewModel(expiredUrl, exceptions)
        viewModel.onWebViewReady()
        viewModel.onLoadError(WebLoadError(expiredUrl, LoadErrorCause.InvalidCertificate, certificateWarning))

        viewModel.continueDespiteWarning(isRemembered = true)

        assertThat(exceptions.requests).containsExactly(SiteException.Certificate("nas.maison", "ab12") to true)
        viewModel.commands.test {
            assertThat(awaitItem()).isEqualTo(BrowserCommand.Load(expiredUrl)) // chargement initial
            assertThat(awaitItem()).isEqualTo(BrowserCommand.Load(expiredUrl))
        }
    }

    @Test
    fun `Given a certificate whose fingerprint is unreadable When the warning shows Then nothing can be allowed`() = runTest {
        val exceptions = FakeSecurityExceptions()
        val viewModel = viewModel(expiredUrl, exceptions)
        val unreadable = certificateWarning.copy(fingerprint = null)
        viewModel.onLoadError(WebLoadError(expiredUrl, LoadErrorCause.InvalidCertificate, unreadable))

        viewModel.continueDespiteWarning(isRemembered = true)

        assertThat(exceptions.requests).isEmpty()
        assertThat(viewModel.pageState.value).isInstanceOf(PageState.Error::class.java)
    }

    @Test
    fun `Given a plain error without warning When the user tries to continue Then nothing is allowed`() = runTest {
        val exceptions = FakeSecurityExceptions()
        val viewModel = viewModel("https://down.example", exceptions)
        viewModel.onLoadError(WebLoadError("https://down.example", LoadErrorCause.Unreachable))

        viewModel.continueDespiteWarning(isRemembered = true)

        assertThat(exceptions.requests).isEmpty()
    }

    @Test
    fun `Given the choice cannot be remembered When the user continues Then the page still loads and a notice explains it`() = runTest {
        val exceptions = FakeSecurityExceptions().apply { failure = "disk full" }
        val viewModel = viewModel(httpUrl, exceptions)

        viewModel.continueDespiteWarning(isRemembered = true)

        assertThat(viewModel.notice.value).isEqualTo(BrowserNotice.SecurityExceptionNotSaved)
        assertThat(viewModel.pageState.value).isInstanceOf(PageState.Loading::class.java)
    }

    @Test
    fun `Given a warning with advanced options open When the user presses Back Then the options fold and the warning stays`() = runTest {
        val viewModel = viewModel(httpUrl)
        viewModel.toggleWarningOptions()
        assertThat(viewModel.layers.value.isWarningOptionsOpen).isTrue()

        viewModel.onBack(canGoBack = true)

        assertThat(viewModel.layers.value.isWarningOptionsOpen).isFalse()
        assertThat((viewModel.pageState.value as PageState.Error).warning).isNotNull()
        viewModel.commands.test { expectNoEvents() }
    }

    @Test
    fun `Given options folded When the user presses Back on a warning over a visited page Then the previous page is shown again`() = runTest {
        val results = "https://duckduckgo.com/?q=box"
        val viewModel = viewModel(results)
        viewModel.onUrlChanged(results, canGoBack = true, canGoForward = false)
        viewModel.onPageFinished(results)
        viewModel.onLoadError(WebLoadError(httpUrl, LoadErrorCause.Unknown, SecurityWarning.InsecureHttp(httpUrl, "192.168.1.10")))

        viewModel.onBack(canGoBack = true)

        assertThat(viewModel.pageState.value).isEqualTo(PageState.Loaded("duckduckgo.com"))
        viewModel.commands.test { expectNoEvents() }
    }

    @Test
    fun `Given a warning on the very first page When the user chooses Return to safety Then the browser returns home`() = runTest {
        val viewModel = viewModel(httpUrl)
        viewModel.toggleWarningOptions()

        viewModel.returnToSafety(canGoBack = false)

        assertThat(viewModel.layers.value.isWarningOptionsOpen).isFalse()
        viewModel.commands.test { assertThat(awaitItem()).isEqualTo(BrowserCommand.Close) }
    }

    @Test
    fun `Given no warning on screen When the options are toggled Then nothing opens`() = runTest {
        val viewModel = viewModel("https://arte.tv")

        viewModel.toggleWarningOptions()

        assertThat(viewModel.layers.value.isWarningOptionsOpen).isFalse()
    }

    @Test
    fun `Given the options open When the layers are read Then Back folds them after closing the interface layers`() {
        val options = BrowserLayers(isWarningOptionsOpen = true)

        assertThat(options.backAction(canGoBack = true)).isEqualTo(BackAction.CollapseWarningOptions)
        assertThat(options.copy(isMenuOpen = true).backAction(canGoBack = true)).isEqualTo(BackAction.CloseMenu)
    }

    @Test
    fun `Given a warning with options open When the interface is read Then the cursor stays off and the interface never hides`() {
        val warning = PageState.Error("192.168.1.10", LoadErrorCause.Unknown, SecurityWarning.InsecureHttp(httpUrl, "192.168.1.10"))

        val hud = browserHudOf(BrowserLayers(isWarningOptionsOpen = true), warning, isIdle = true)

        assertThat(hud.isCursorVisible).isFalse()
        assertThat(hud.isHidden).isFalse()
    }

    @Test
    fun `Given an http page shown by exception When the toolbar is read Then it says not secure instead of showing a padlock`() = runTest {
        val viewModel = viewModel(httpUrl, FakeSecurityExceptions(SiteException.Http("192.168.1.10")))
        viewModel.onPageStarted(httpUrl)
        viewModel.onPageFinished(httpUrl)

        viewModel.toolbar.test {
            val state = expectMostRecentItem()
            assertThat(state.insecurity).isEqualTo(PageInsecurity.Http)
            assertThat(state.isSecure).isFalse()
        }
    }

    @Test
    fun `Given an http page shown by exception When it opens Then one notice warns not to enter sensitive data`() = runTest {
        val viewModel = viewModel(httpUrl, FakeSecurityExceptions(SiteException.Http("192.168.1.10")))

        viewModel.onPageStarted(httpUrl)

        assertThat(viewModel.notice.value).isEqualTo(BrowserNotice.InsecureHttpShown)
    }

    @Test
    fun `Given a page shown with a trusted certificate When the toolbar is read Then it says certificate not verified without padlock`() = runTest {
        val viewModel = viewModel(expiredUrl)
        viewModel.onUnverifiedCertificateAccepted(expiredUrl)
        viewModel.onPageStarted(expiredUrl)
        viewModel.onPageFinished(expiredUrl)

        assertThat(viewModel.notice.value).isEqualTo(BrowserNotice.UnverifiedCertificateShown)
        viewModel.toolbar.test {
            val state = expectMostRecentItem()
            assertThat(state.insecurity).isEqualTo(PageInsecurity.UnverifiedCertificate)
            assertThat(state.isSecure).isFalse()
        }
    }

    @Test
    fun `Given a page with an unverified certificate When the user navigates to another site Then the badge goes away and the padlock returns`() = runTest {
        val viewModel = viewModel(expiredUrl)
        viewModel.onUnverifiedCertificateAccepted(expiredUrl)
        viewModel.onPageStarted(expiredUrl)

        viewModel.onPageStarted("https://arte.tv/")
        viewModel.onPageFinished("https://arte.tv/")

        viewModel.toolbar.test {
            val state = expectMostRecentItem()
            assertThat(state.insecurity).isNull()
            assertThat(state.isSecure).isTrue()
        }
    }

    @Test
    fun `Given a trusted certificate page left for another site When the user comes back without a new SSL check Then the badge returns`() = runTest {
        val viewModel = viewModel(expiredUrl)
        viewModel.onUnverifiedCertificateAccepted(expiredUrl)
        viewModel.onPageStarted(expiredUrl)
        viewModel.onPageStarted("https://arte.tv/")

        // Le moteur a retenu le proceed() : seul le garde de la WebView signale de nouveau la page, après son début.
        viewModel.onPageStarted(expiredUrl)
        viewModel.onUnverifiedCertificateAccepted(expiredUrl)
        viewModel.onPageFinished(expiredUrl)

        viewModel.toolbar.test {
            val state = expectMostRecentItem()
            assertThat(state.insecurity).isEqualTo(PageInsecurity.UnverifiedCertificate)
            assertThat(state.isSecure).isFalse()
        }
    }

    @Test
    fun `Given an http page reached from history without exception When the warning replaces it and the user presses Back Then the browser goes back`() = runTest {
        val viewModel = viewModel("https://arte.tv/")
        viewModel.onWebViewReady()
        viewModel.commands.test {
            assertThat(awaitItem()).isEqualTo(BrowserCommand.Load("https://arte.tv/"))
            viewModel.onUrlChanged(httpUrl, canGoBack = true, canGoForward = true)
            viewModel.onLoadError(WebLoadError(httpUrl, LoadErrorCause.Unknown, SecurityWarning.InsecureHttp(httpUrl, "192.168.1.10")))

            viewModel.onBack(canGoBack = true)

            assertThat(awaitItem()).isEqualTo(BrowserCommand.GoBack)
        }
        assertThat((viewModel.pageState.value as PageState.Error).warning).isNotNull()
    }
}

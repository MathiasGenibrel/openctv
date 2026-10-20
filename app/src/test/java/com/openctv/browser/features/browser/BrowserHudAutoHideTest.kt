package com.openctv.browser.features.browser

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.openctv.browser.features.browser.domain.BrowserLayers
import com.openctv.browser.features.browser.ui.BrowserCommand
import com.openctv.browser.features.browser.ui.BrowserViewModel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserHudAutoHideTest {

    private val url = "https://www.arte.tv/fr/"
    private var hideDelay: Duration? = 5.seconds

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Page chargée, interface observée (le HUD n'est calculé que s'il a un abonné). */
    private fun TestScope.loadedViewModel(): BrowserViewModel {
        val viewModel = BrowserViewModel(url, FakeBookmarks(), hudHideDelay = { hideDelay })
        backgroundScope.launch { viewModel.hud.collect {} }
        viewModel.onPageStarted(url)
        viewModel.onPageFinished(url)
        return viewModel
    }

    private suspend fun TestScope.idleFor(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `Given a loaded page When the user is idle for the delay Then the HUD hides`() = runTest {
        val viewModel = loadedViewModel()

        idleFor(4_999)
        assertThat(viewModel.hud.value.isHidden).isFalse()
        idleFor(1)

        assertThat(viewModel.hud.value.isHidden).isTrue()
    }

    @Test
    fun `Given the delay is Never When the user is idle Then the HUD stays`() = runTest {
        hideDelay = null
        val viewModel = loadedViewModel()

        idleFor(60_000)

        assertThat(viewModel.hud.value.isHidden).isFalse()
    }

    @Test
    fun `Given a hidden HUD When the user presses OK or an arrow Then it only reveals and nothing reaches the page`() =
        runTest {
            val viewModel = loadedViewModel()
            idleFor(5_000)

            val isSwallowed = viewModel.onRemoteKey(isBack = false)

            assertThat(isSwallowed).isTrue()
            assertThat(viewModel.hud.value.isHidden).isFalse()
        }

    @Test
    fun `Given a visible HUD When the user presses a key Then the key goes through and the timer restarts`() = runTest {
        val viewModel = loadedViewModel()
        idleFor(4_000)

        val isSwallowed = viewModel.onRemoteKey(isBack = false)
        idleFor(4_999)

        assertThat(isSwallowed).isFalse()
        assertThat(viewModel.hud.value.isHidden).isFalse()
        idleFor(1)
        assertThat(viewModel.hud.value.isHidden).isTrue()
    }

    @Test
    fun `Given a hidden HUD When the user presses Back Then the HUD returns and Back keeps its meaning`() = runTest {
        val viewModel = loadedViewModel()
        idleFor(5_000)

        val isSwallowed = viewModel.onRemoteKey(isBack = true)
        viewModel.onBack(canGoBack = true)

        assertThat(isSwallowed).isFalse()
        assertThat(viewModel.hud.value.isHidden).isFalse()
        viewModel.commands.test { assertThat(awaitItem()).isEqualTo(BrowserCommand.GoBack) }
    }

    @Test
    fun `Given a menu open When the user is idle Then the HUD stays and the delay restarts once it closes`() = runTest {
        val viewModel = loadedViewModel()
        viewModel.openMenu()

        idleFor(30_000)
        assertThat(viewModel.hud.value.isHidden).isFalse()

        viewModel.closeMenu()
        idleFor(4_999)
        assertThat(viewModel.hud.value.isHidden).isFalse()
        idleFor(1)
        assertThat(viewModel.hud.value.isHidden).isTrue()
    }

    @Test
    fun `Given a loading page When the user is idle Then the HUD stays until the page is loaded`() = runTest {
        val viewModel = BrowserViewModel(url, FakeBookmarks(), hudHideDelay = { hideDelay })
        backgroundScope.launch { viewModel.hud.collect {} }

        idleFor(30_000)
        assertThat(viewModel.hud.value.isHidden).isFalse()

        viewModel.onPageFinished(url)
        idleFor(5_000)
        assertThat(viewModel.hud.value.isHidden).isTrue()
    }

    @Test
    fun `Given the user moves the cursor When keys keep coming Then the HUD never hides`() = runTest {
        val viewModel = loadedViewModel()

        repeat(4) {
            idleFor(4_000)
            viewModel.onRemoteKey(isBack = false)
        }

        assertThat(viewModel.hud.value.isHidden).isFalse()
        assertThat(viewModel.layers.value).isEqualTo(BrowserLayers())
    }

    @Test
    fun `Given the user changes the delay setting When the next key is pressed Then the new delay applies`() = runTest {
        val viewModel = loadedViewModel()
        hideDelay = 3.seconds

        viewModel.onRemoteKey(isBack = false)
        idleFor(3_000)

        assertThat(viewModel.hud.value.isHidden).isTrue()
    }
}

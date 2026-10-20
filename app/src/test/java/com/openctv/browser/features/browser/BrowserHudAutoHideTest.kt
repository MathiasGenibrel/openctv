package com.openctv.browser.features.browser

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import android.webkit.WebView
import androidx.compose.ui.input.key.KeyEvent
import com.openctv.browser.features.browser.domain.BrowserLayers
import com.openctv.browser.features.browser.domain.KeyPhase
import com.openctv.browser.features.browser.domain.RemoteKey
import com.openctv.browser.features.browser.ui.BrowserCommand
import com.openctv.browser.features.browser.ui.BrowserViewModel
import com.openctv.browser.features.browser.ui.PageKeyHandler
import com.openctv.browser.features.browser.ui.consumesKey
import io.mockk.every
import io.mockk.mockk
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
    private val bookmarks = FakeBookmarks()
    private val okPress = RemoteKey(code = 23L, phase = KeyPhase.Down)
    private val backPress = RemoteKey(code = 4L, phase = KeyPhase.Down, isBack = true)

    /** Touches reçues par le curseur virtuel (il prend toutes les flèches et OK de la page). */
    private val cursorKeys = mutableListOf<KeyEvent>()
    private val cursor = PageKeyHandler { event -> cursorKeys += event; true }
    private val webView = mockk<WebView>(relaxed = true)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Page chargée, interface observée (le HUD n'est calculé que s'il a un abonné). */
    private fun TestScope.loadedViewModel(): BrowserViewModel {
        val viewModel = BrowserViewModel(url, bookmarks, hudHideDelay = { hideDelay })
        backgroundScope.launch { viewModel.hud.collect {} }
        viewModel.onPageStarted(url)
        viewModel.onPageFinished(url)
        return viewModel
    }

    private suspend fun TestScope.idleFor(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    /** Passe la touche par l'aiguillage réel de l'écran ; `true` = consommée avant la barre et la WebView. */
    private fun BrowserViewModel.send(keyCode: Int, action: Int, repeatCount: Int = 0): Boolean {
        val native = mockk<android.view.KeyEvent> {
            every { this@mockk.keyCode } returns keyCode
            every { this@mockk.action } returns action
            every { this@mockk.repeatCount } returns repeatCount
        }
        return consumesKey(KeyEvent(native), webView, cursor)
    }

    private fun BrowserViewModel.down(keyCode: Int, repeatCount: Int = 0) =
        send(keyCode, android.view.KeyEvent.ACTION_DOWN, repeatCount)

    private fun BrowserViewModel.up(keyCode: Int) = send(keyCode, android.view.KeyEvent.ACTION_UP)

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

            val isSwallowed = viewModel.onRemoteKey(okPress)

            assertThat(isSwallowed).isTrue()
            assertThat(viewModel.hud.value.isHidden).isFalse()
        }

    @Test
    fun `Given a visible HUD When the user presses a key Then the key goes through and the timer restarts`() = runTest {
        val viewModel = loadedViewModel()
        idleFor(4_000)

        val isSwallowed = viewModel.onRemoteKey(okPress)
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

        val isSwallowed = viewModel.onRemoteKey(backPress)
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
            viewModel.onRemoteKey(okPress)
        }

        assertThat(viewModel.hud.value.isHidden).isFalse()
        assertThat(viewModel.layers.value).isEqualTo(BrowserLayers())
    }

    @Test
    fun `Given the user changes the delay setting When the next key is pressed Then the new delay applies`() = runTest {
        val viewModel = loadedViewModel()
        hideDelay = 3.seconds

        viewModel.onRemoteKey(okPress)
        idleFor(3_000)

        assertThat(viewModel.hud.value.isHidden).isTrue()
    }

    @Test
    fun `Given a hidden HUD When the user holds an arrow to reveal it Then its press, repeats and release never reach the cursor`() =
        runTest {
            val viewModel = loadedViewModel()
            idleFor(5_000)

            val swallowed = listOf(
                viewModel.down(RIGHT),
                viewModel.down(RIGHT, repeatCount = 1),
                viewModel.down(RIGHT, repeatCount = 2),
                viewModel.up(RIGHT),
            )

            assertThat(swallowed).containsExactly(true, true, true, true)
            assertThat(cursorKeys).isEmpty()
            assertThat(viewModel.hud.value.isHidden).isFalse()
        }

    @Test
    fun `Given the reveal key was released When the user presses it again Then the cursor receives it normally`() =
        runTest {
            val viewModel = loadedViewModel()
            idleFor(5_000)
            viewModel.down(OK)
            viewModel.down(OK, repeatCount = 1)
            viewModel.up(OK)

            viewModel.down(OK)
            viewModel.up(OK)

            assertThat(cursorKeys).hasSize(2)
        }

    @Test
    fun `Given the reveal key is still held When the user presses another key Then only the other key reaches the cursor`() =
        runTest {
            val viewModel = loadedViewModel()
            idleFor(5_000)
            viewModel.down(RIGHT)

            viewModel.down(LEFT)
            viewModel.down(RIGHT, repeatCount = 1)
            viewModel.up(RIGHT)
            viewModel.up(LEFT)

            assertThat(cursorKeys.map { it.nativeKeyEvent.keyCode }).containsExactly(LEFT, LEFT)
        }

    @Test
    fun `Given a hidden toolbar keeps the focus When the user presses OK Then it only reveals and no button is clicked`() =
        runTest {
            val viewModel = loadedViewModel()
            viewModel.enterInterfaceMode()
            idleFor(5_000)
            assertThat(viewModel.hud.value.isHidden).isTrue()

            val isDownSwallowed = viewModel.down(OK)
            val isUpSwallowed = viewModel.up(OK)
            val isNextPressSwallowed = viewModel.down(OK)

            // Le bouton focalisé clique au relâchement : ni l'appui ni le relâchement ne lui parviennent.
            assertThat(isDownSwallowed).isTrue()
            assertThat(isUpSwallowed).isTrue()
            assertThat(isNextPressSwallowed).isFalse()
        }

    @Test
    fun `Given a 3 second delay When a 4 second message appears Then the HUD stays until the message ends and the delay restarts`() =
        runTest {
            hideDelay = 3.seconds
            val viewModel = loadedViewModel()
            bookmarks.failure = "Stockage indisponible"
            idleFor(2_000)

            viewModel.toggleBookmark()
            idleFor(3_999)
            assertThat(viewModel.notice.value).isNotNull()
            assertThat(viewModel.hud.value.isHidden).isFalse()
            idleFor(1)
            assertThat(viewModel.notice.value).isNull()
            assertThat(viewModel.hud.value.isHidden).isFalse()

            idleFor(2_999)
            assertThat(viewModel.hud.value.isHidden).isFalse()
            idleFor(1)
            assertThat(viewModel.hud.value.isHidden).isTrue()
        }

    private companion object {
        const val LEFT = android.view.KeyEvent.KEYCODE_DPAD_LEFT
        const val RIGHT = android.view.KeyEvent.KEYCODE_DPAD_RIGHT
        const val OK = android.view.KeyEvent.KEYCODE_DPAD_CENTER
    }
}

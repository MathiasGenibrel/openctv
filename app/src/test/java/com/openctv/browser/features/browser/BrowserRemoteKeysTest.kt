package com.openctv.browser.features.browser

import android.view.KeyEvent
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.remote.RemoteAction
import com.openctv.browser.core.remote.ScrollDirection
import com.openctv.browser.core.webview.MediaCommand
import com.openctv.browser.features.browser.ui.BrowserCommand
import com.openctv.browser.features.browser.ui.BrowserViewModel
import com.openctv.browser.features.browser.ui.FullscreenKeys
import com.openctv.browser.features.search.domain.SearchMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Touches « confort » de la télécommande : routage selon la page, les surcouches et le plein écran. */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserRemoteKeysTest {

    private val url = "https://www.arte.tv/fr/"
    private val bookmarks = FakeBookmarks()
    private val scrollDown = RemoteAction.ScrollPage(ScrollDirection.Down)
    private val togglePlayback = RemoteAction.Media(MediaCommand.TogglePlayback)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = BrowserViewModel(url, bookmarks).also { drainInitialLoad(it) }

    private fun drainInitialLoad(viewModel: BrowserViewModel) = viewModel.onWebViewReady()

    @Test
    fun `Given a page shown When Channel Down is pressed Then the page is asked to scroll down one screen`() = runTest {
        val viewModel = viewModel()
        viewModel.commands.test {
            skipItems(1) // chargement initial
            val isConsumed = viewModel.onRemoteAction(scrollDown, isRepeat = false)

            assertThat(isConsumed).isTrue()
            assertThat(awaitItem()).isEqualTo(BrowserCommand.ScrollPage(ScrollDirection.Down))
        }
    }

    @Test
    fun `Given a video in the page outside fullscreen When Play-Pause is pressed Then the video command is sent`() = runTest {
        val viewModel = viewModel()
        viewModel.commands.test {
            skipItems(1)
            viewModel.onRemoteAction(togglePlayback, isRepeat = false)

            assertThat(awaitItem()).isEqualTo(BrowserCommand.Media(MediaCommand.TogglePlayback))
        }
    }

    @Test
    fun `Given Play-Pause held down When it repeats Then the video is not toggled again`() = runTest {
        val viewModel = viewModel()
        viewModel.commands.test {
            skipItems(1)
            val isConsumed = viewModel.onRemoteAction(togglePlayback, isRepeat = true)

            assertThat(isConsumed).isTrue()
            expectNoEvents()
        }
    }

    @Test
    fun `Given the menu open When a page key is pressed Then it is not consumed and the page does not move`() = runTest {
        val viewModel = viewModel()
        viewModel.openMenu()
        viewModel.commands.test {
            skipItems(1)
            val isConsumed = viewModel.onRemoteAction(scrollDown, isRepeat = false)

            assertThat(isConsumed).isFalse()
            expectNoEvents()
        }
    }

    @Test
    fun `Given the address input open When a digit is pressed Then it is left to the text field`() = runTest {
        bookmarks.seed("https://one.example/")
        val viewModel = viewModel()
        viewModel.openAddressInput()

        val isConsumed = viewModel.onRemoteAction(RemoteAction.OpenFavorite(1), isRepeat = false)

        assertThat(isConsumed).isFalse()
        assertThat(viewModel.layers.value.addressInput).isNotNull()
    }

    @Test
    fun `Given a video in fullscreen When a channel or media key reaches the page Then nothing happens under the player`() = runTest {
        val viewModel = viewModel()
        viewModel.onFullscreenShown()
        viewModel.commands.test {
            skipItems(1)
            val isScrollConsumed = viewModel.onRemoteAction(scrollDown, isRepeat = false)
            val isMediaConsumed = viewModel.onRemoteAction(togglePlayback, isRepeat = false)

            assertThat(isScrollConsumed).isFalse()
            assertThat(isMediaConsumed).isFalse()
            expectNoEvents()
        }
    }

    @Test
    fun `Given the search key When it is pressed on a page Then the address input opens prefilled with the page address`() = runTest {
        val viewModel = viewModel()

        val isConsumed = viewModel.onRemoteAction(RemoteAction.OpenSearch, isRepeat = false)

        assertThat(isConsumed).isTrue()
        val request = viewModel.layers.value.addressInput
        assertThat(request?.mode).isEqualTo(SearchMode.Browse)
        assertThat(request?.initialText).isEqualTo(url)
    }

    @Test
    fun `Given the info key When it is pressed Then the browser menu opens`() = runTest {
        val viewModel = viewModel()

        viewModel.onRemoteAction(RemoteAction.OpenMenu, isRepeat = false)

        assertThat(viewModel.layers.value.isMenuOpen).isTrue()
    }

    @Test
    fun `Given two favorites When digit 2 is pressed Then the second favorite is loaded and the interface returns to the page`() = runTest {
        bookmarks.seed("https://one.example/")
        bookmarks.seed("https://two.example/")
        val viewModel = viewModel()
        viewModel.enterInterfaceMode()
        viewModel.commands.test {
            skipItems(1)
            viewModel.onRemoteAction(RemoteAction.OpenFavorite(2), isRepeat = false)

            assertThat(awaitItem()).isEqualTo(BrowserCommand.Load("https://two.example"))
            assertThat(viewModel.layers.value.isInterfaceMode).isFalse()
        }
    }

    @Test
    fun `Given one favorite When digit 5 is pressed Then nothing is loaded`() = runTest {
        bookmarks.seed("https://one.example/")
        val viewModel = viewModel()
        viewModel.commands.test {
            skipItems(1)
            viewModel.onRemoteAction(RemoteAction.OpenFavorite(5), isRepeat = false)

            expectNoEvents()
        }
    }

    @Test
    fun `Given a video in fullscreen When Play and Pause keys are pressed Then the player gets the matching command`() {
        assertThat(FullscreenKeys.commandFor(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.ACTION_DOWN, 0)).isEqualTo(MediaCommand.Play)
        assertThat(FullscreenKeys.commandFor(KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.ACTION_DOWN, 0)).isEqualTo(MediaCommand.Pause)
        assertThat(FullscreenKeys.commandFor(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.ACTION_DOWN, 4)).isEqualTo(MediaCommand.SeekForward)
        assertThat(FullscreenKeys.commandFor(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.ACTION_DOWN, 4)).isNull()
    }

    @Test
    fun `Given a video in fullscreen When Channel keys are pressed Then the player does not take them`() {
        assertThat(FullscreenKeys.isPlayerKey(KeyEvent.KEYCODE_CHANNEL_DOWN)).isFalse()
        assertThat(FullscreenKeys.isPlayerKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)).isTrue()
    }
}

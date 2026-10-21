package com.openctv.browser.features.browser

import android.view.View
import android.webkit.WebChromeClient
import com.google.common.truth.Truth.assertThat
import android.view.KeyEvent
import com.openctv.browser.features.browser.ui.BrowserFullscreen
import com.openctv.browser.features.browser.ui.FullscreenKeys
import com.openctv.browser.features.browser.ui.FullscreenPresenter
import com.openctv.browser.features.browser.ui.PlayerAction
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class BrowserFullscreenTest {
    private var shown = 0
    private var hidden = 0
    private val fullscreen = BrowserFullscreen(onShown = { shown++ }, onHidden = { hidden++ })

    private val player = mockk<View>()
    private val playerCallback = mockk<WebChromeClient.CustomViewCallback>(relaxed = true)

    @Test
    fun `Given a video in fullscreen When the page asks for a second fullscreen Then the second is handed back and the first player stays`() {
        // Given
        fullscreen.onShow(player, playerCallback)
        val secondPlayer = mockk<View>()
        val secondCallback = mockk<WebChromeClient.CustomViewCallback>(relaxed = true)

        // When
        fullscreen.onShow(secondPlayer, secondCallback)

        // Then
        verify(exactly = 1) { secondCallback.onCustomViewHidden() }
        verify(exactly = 0) { playerCallback.onCustomViewHidden() }
        assertThat(fullscreen.view).isSameInstanceAs(player)
        assertThat(shown).isEqualTo(1)
    }

    @Test
    fun `Given a video in fullscreen When the user exits and the WebView confirms Then the player is released and the screen notified once`() {
        // Given
        fullscreen.onShow(player, playerCallback)

        // When
        fullscreen.exit()
        fullscreen.onHide()

        // Then
        verify(exactly = 1) { playerCallback.onCustomViewHidden() }
        assertThat(fullscreen.view).isNull()
        assertThat(hidden).isEqualTo(1)
    }

    @Test
    fun `Given no video in fullscreen When the browser screen is torn down Then nothing is handed back to the page`() {
        // When
        fullscreen.exit()

        // Then
        assertThat(hidden).isEqualTo(0)
        assertThat(fullscreen.view).isNull()
    }

    @Test
    fun `Given a fullscreen left by the page itself When the screen is torn down Then the player is not handed back twice`() {
        // Given
        fullscreen.onShow(player, playerCallback)
        fullscreen.onHide()

        // When
        fullscreen.exit()

        // Then
        verify(exactly = 0) { playerCallback.onCustomViewHidden() }
        assertThat(hidden).isEqualTo(1)
    }

    @Test
    fun `Given a page asking for fullscreen When the player is shown Then it is mounted in the same call before the screen is notified`() {
        // Given
        val order = mutableListOf<String>()
        val presenter = object : FullscreenPresenter {
            override fun show(video: View) { order += "mounted" }
            override fun hide(video: View) { order += "unmounted" }
        }
        val mounted = BrowserFullscreen(onShown = { order += "notified" }, onHidden = { order += "released" }, presenter = presenter)

        // When
        mounted.onShow(player, playerCallback)
        mounted.onHide()

        // Then
        assertThat(order).containsExactly("mounted", "notified", "unmounted", "released").inOrder()
    }

    @Test
    fun `Given a second fullscreen request When it is refused Then nothing is mounted for it`() {
        // Given
        var mounts = 0
        val presenter = object : FullscreenPresenter {
            override fun show(video: View) { mounts++ }
            override fun hide(video: View) = Unit
        }
        val mounted = BrowserFullscreen(onShown = {}, onHidden = {}, presenter = presenter)
        mounted.onShow(player, playerCallback)

        // When
        mounted.onShow(mockk<View>(), mockk(relaxed = true))

        // Then
        assertThat(mounts).isEqualTo(1)
    }

    @Test
    fun `Given a video in fullscreen When the user presses OK once Then playback is toggled`() {
        assertThat(FullscreenKeys.actionFor(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN, repeatCount = 0))
            .isEqualTo(PlayerAction.TogglePlayback)
    }

    @Test
    fun `Given a video in fullscreen When OK is held Then playback does not flicker and the release does nothing`() {
        assertThat(FullscreenKeys.actionFor(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN, repeatCount = 3))
            .isEqualTo(PlayerAction.None)
        assertThat(FullscreenKeys.actionFor(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_UP, repeatCount = 0))
            .isEqualTo(PlayerAction.None)
    }

    @Test
    fun `Given a video in fullscreen When the user presses left or right Then the player seeks and a held key keeps seeking`() {
        assertThat(FullscreenKeys.actionFor(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN, repeatCount = 0))
            .isEqualTo(PlayerAction.SeekBack)
        assertThat(FullscreenKeys.actionFor(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.ACTION_DOWN, repeatCount = 5))
            .isEqualTo(PlayerAction.SeekForward)
    }

    @Test
    fun `Given a video in fullscreen When the user presses any arrow or OK Then the page never receives it but Back is not taken`() {
        val playerKeys = listOf(
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        )

        assertThat(playerKeys.all(FullscreenKeys::isPlayerKey)).isTrue()
        assertThat(FullscreenKeys.isPlayerKey(KeyEvent.KEYCODE_BACK)).isFalse()
    }
}

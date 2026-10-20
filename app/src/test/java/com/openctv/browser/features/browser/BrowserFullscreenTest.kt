package com.openctv.browser.features.browser

import android.view.View
import android.webkit.WebChromeClient
import com.google.common.truth.Truth.assertThat
import com.openctv.browser.features.browser.ui.BrowserFullscreen
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
}

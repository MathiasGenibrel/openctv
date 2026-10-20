package com.openctv.browser.features.browser

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.webview.LoadErrorCause
import com.openctv.browser.features.browser.domain.BrowserLayers
import com.openctv.browser.features.browser.domain.BrowserMode
import com.openctv.browser.features.browser.domain.HudAutoHide
import com.openctv.browser.features.browser.domain.PageState
import com.openctv.browser.features.browser.domain.browserHudOf
import com.openctv.browser.features.search.domain.SearchMode
import com.openctv.browser.features.search.domain.SearchRequest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HudAutoHideTest {

    @Test
    fun `Given a delay of 5 seconds When the user stays idle for 5 seconds Then the interface is idle`() = runTest {
        val autoHide = HudAutoHide(backgroundScope) { 5.seconds }
        autoHide.onUserActivity()

        advanceTimeBy(4_999)
        runCurrent()
        assertThat(autoHide.isIdle.value).isFalse()

        advanceTimeBy(1)
        runCurrent()
        assertThat(autoHide.isIdle.value).isTrue()
    }

    @Test
    fun `Given the interface is idle When the user presses a key Then it comes back and the timer restarts`() = runTest {
        val autoHide = HudAutoHide(backgroundScope) { 5.seconds }
        autoHide.onUserActivity()
        advanceTimeBy(5_000)
        runCurrent()

        autoHide.onUserActivity()
        assertThat(autoHide.isIdle.value).isFalse()
        advanceTimeBy(4_999)
        runCurrent()
        assertThat(autoHide.isIdle.value).isFalse()
        advanceTimeBy(1)
        runCurrent()
        assertThat(autoHide.isIdle.value).isTrue()
    }

    @Test
    fun `Given the delay is Never When the user stays idle for a long time Then the interface never hides`() = runTest {
        val autoHide = HudAutoHide(backgroundScope) { null }
        autoHide.onUserActivity()

        advanceTimeBy(10 * 60_000L)
        runCurrent()

        assertThat(autoHide.isIdle.value).isFalse()
    }

    @Test
    fun `Given the user changes the delay setting When the next activity happens Then the new delay applies`() = runTest {
        var delay: Duration? = 10.seconds
        val autoHide = HudAutoHide(backgroundScope) { delay }
        autoHide.onUserActivity()

        delay = 3.seconds
        autoHide.onUserActivity()
        advanceTimeBy(3_000)
        runCurrent()

        assertThat(autoHide.isIdle.value).isTrue()
    }

    private val menuOpen = BrowserLayers(isMenuOpen = true)
    private val inputOpen = BrowserLayers(addressInput = SearchRequest(SearchMode.Browse, initialText = ""))
    private val loaded = PageState.Loaded("arte.tv")

    @Test
    fun `Given an idle user on a loaded page When the HUD is computed Then it is hidden with the pointer`() {
        val hud = browserHudOf(BrowserLayers(), loaded, isIdle = true)

        assertThat(hud.isHidden).isTrue()
        assertThat(hud.isCursorShown).isFalse()
        // Le curseur garde les touches : sinon il relâcherait tout et injecterait un UP.
        assertThat(hud.isCursorVisible).isTrue()
    }

    @Test
    fun `Given an active user When the HUD is computed Then the pointer is shown`() {
        val hud = browserHudOf(BrowserLayers(), loaded, isIdle = false)

        assertThat(hud.isHidden).isFalse()
        assertThat(hud.isCursorShown).isTrue()
    }

    @Test
    fun `Given an idle user in the interface mode When the HUD is computed Then it is hidden but the toolbar stays composed`() {
        val hud = browserHudOf(BrowserLayers(mode = BrowserMode.Interface), loaded, isIdle = true)

        assertThat(hud.isHidden).isTrue()
        assertThat(hud.isToolbarShown).isTrue()
    }

    @Test
    fun `Given a menu an input a loading page or an error When the user is idle Then the HUD never hides`() {
        val loading = PageState.Loading("arte.tv", 0.4f)
        val failed = PageState.Error("arte.tv", LoadErrorCause.Unreachable)

        assertThat(browserHudOf(menuOpen, loaded, isIdle = true).isHidden).isFalse()
        assertThat(browserHudOf(inputOpen, loaded, isIdle = true).isHidden).isFalse()
        assertThat(browserHudOf(BrowserLayers(), loading, isIdle = true).isHidden).isFalse()
        assertThat(browserHudOf(BrowserLayers(), failed, isIdle = true).isHidden).isFalse()
    }

    @Test
    fun `Given a fullscreen video When the user is idle Then there is no OpenCTV interface to hide`() {
        val hud = browserHudOf(BrowserLayers(isFullscreen = true), loaded, isIdle = true)

        assertThat(hud.isHidden).isFalse()
        assertThat(hud.isCursorShown).isFalse()
    }
}

package com.openctv.browser.features.browser

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.webview.LoadErrorCause
import com.openctv.browser.features.browser.domain.BackAction
import com.openctv.browser.features.browser.domain.BrowserLayers
import com.openctv.browser.features.browser.domain.BrowserMode
import com.openctv.browser.features.browser.domain.PageState
import com.openctv.browser.features.browser.domain.PageUnderError
import com.openctv.browser.features.browser.domain.backAction
import com.openctv.browser.features.browser.domain.browserHudOf
import com.openctv.browser.features.search.domain.SearchMode
import com.openctv.browser.features.search.domain.SearchRequest
import org.junit.Test

class BrowserLayersTest {

    private val input = SearchRequest(SearchMode.Browse, initialText = "https://arte.tv")
    private val everythingOpen = BrowserLayers(addressInput = input, isMenuOpen = true, mode = BrowserMode.Interface)

    @Test
    fun `Given every layer open When the user keeps pressing Back Then layers close top first then history then home`() {
        val steps = listOf(
            everythingOpen to BackAction.CloseAddressInput,
            everythingOpen.copy(addressInput = null) to BackAction.CloseMenu,
            BrowserLayers(mode = BrowserMode.Interface) to BackAction.ExitInterface,
            BrowserLayers() to BackAction.GoBackInHistory,
        )

        steps.forEach { (layers, expected) ->
            assertThat(layers.backAction(canGoBack = true)).isEqualTo(expected)
        }
        assertThat(BrowserLayers().backAction(canGoBack = false)).isEqualTo(BackAction.LeaveBrowser)
    }

    @Test
    fun `Given an overlay open When Back is pressed without history Then the overlay closes and the browser stays`() {
        val menuOnly = BrowserLayers(isMenuOpen = true)

        assertThat(menuOnly.backAction(canGoBack = false)).isEqualTo(BackAction.CloseMenu)
    }

    @Test
    fun `Given a security warning over the first page of the history When Back is pressed Then that page is shown again instead of leaving`() {
        val action = BrowserLayers().backAction(canGoBack = false, pageUnderError = PageUnderError.BehindSecurityWarning)

        assertThat(action).isEqualTo(BackAction.DismissError)
    }

    @Test
    fun `Given a load error over a previous page When Back is pressed Then the page is shown again only if the history goes further back`() {
        val layers = BrowserLayers()

        assertThat(layers.backAction(canGoBack = true, pageUnderError = PageUnderError.BehindLoadError))
            .isEqualTo(BackAction.DismissError)
        assertThat(layers.backAction(canGoBack = false, pageUnderError = PageUnderError.BehindLoadError))
            .isEqualTo(BackAction.LeaveBrowser)
    }

    @Test
    fun `Given a security warning with its options open When Back is pressed Then the options fold before the page is shown again`() {
        val layers = BrowserLayers(isWarningOptionsOpen = true)

        assertThat(layers.backAction(canGoBack = false, pageUnderError = PageUnderError.BehindSecurityWarning))
            .isEqualTo(BackAction.CollapseWarningOptions)
    }

    @Test
    fun `Given the page mode When a page is loading Then the toolbar shows its progress without taking the focus`() {
        val hud = browserHudOf(BrowserLayers(), PageState.Loading("arte.tv", 0.3f))

        assertThat(hud.isToolbarShown).isTrue()
        assertThat(hud.isToolbarInteractive).isFalse()
        assertThat(hud.isInterfaceHelpShown).isFalse()
    }

    @Test
    fun `Given the page mode When the page is loaded or failed Then no toolbar covers the page`() {
        val loaded = browserHudOf(BrowserLayers(), PageState.Loaded("arte.tv"))
        val failed = browserHudOf(BrowserLayers(), PageState.Error("arte.tv", LoadErrorCause.Unreachable))

        assertThat(loaded.isToolbarShown).isFalse()
        assertThat(failed.isToolbarShown).isFalse()
    }

    @Test
    fun `Given the interface mode When an overlay opens over it Then the toolbar stays and the help hides`() {
        val interfaceMode = BrowserLayers(mode = BrowserMode.Interface)
        val loaded = PageState.Loaded("arte.tv")

        assertThat(browserHudOf(interfaceMode, loaded).isInterfaceHelpShown).isTrue()
        val underMenu = browserHudOf(interfaceMode.copy(isMenuOpen = true), loaded)
        assertThat(underMenu.isToolbarInteractive).isTrue()
        assertThat(underMenu.isInterfaceHelpShown).isFalse()
    }

    @Test
    fun `Given a video in fullscreen over every other layer When Back is pressed Then only the fullscreen exits`() {
        val layers = everythingOpen.copy(isFullscreen = true)

        assertThat(layers.backAction(canGoBack = true)).isEqualTo(BackAction.ExitFullscreen)
        assertThat(layers.backAction(canGoBack = false)).isEqualTo(BackAction.ExitFullscreen)
    }

    @Test
    fun `Given a video in fullscreen When the interface state is computed Then no toolbar or help covers the player`() {
        val fullscreen = BrowserLayers(mode = BrowserMode.Interface, isFullscreen = true)

        val loading = browserHudOf(fullscreen, PageState.Loading("youtube.com", 0.5f))
        val loaded = browserHudOf(fullscreen, PageState.Loaded("youtube.com"))

        listOf(loading, loaded).forEach { hud ->
            assertThat(hud.isToolbarShown).isFalse()
            assertThat(hud.isToolbarInteractive).isFalse()
            assertThat(hud.isInterfaceHelpShown).isFalse()
        }
    }

    @Test
    fun `Given a video in fullscreen When the layers are read Then the fullscreen counts as an open overlay`() {
        assertThat(BrowserLayers(isFullscreen = true).isOverlayOpen).isTrue()
    }

    @Test
    fun `Given the page mode without overlay When the page is shown Then the cursor is visible with its help`() {
        val hud = browserHudOf(BrowserLayers(), PageState.Loaded("arte.tv"), isCursorHelpRequested = true)

        assertThat(hud.isCursorVisible).isTrue()
        assertThat(hud.isCursorHelpShown).isTrue()
    }

    @Test
    fun `Given the interface mode or an overlay or an error or a fullscreen video Then the cursor is hidden`() {
        val loaded = PageState.Loaded("arte.tv")
        val hidden = listOf(
            browserHudOf(BrowserLayers(mode = BrowserMode.Interface), loaded, true),
            browserHudOf(BrowserLayers(isMenuOpen = true), loaded, true),
            browserHudOf(BrowserLayers(addressInput = input), loaded, true),
            browserHudOf(BrowserLayers(isFullscreen = true), loaded, true),
            browserHudOf(BrowserLayers(), PageState.Error("arte.tv", LoadErrorCause.Unreachable), true),
        )

        hidden.forEach { hud ->
            assertThat(hud.isCursorVisible).isFalse()
            assertThat(hud.isCursorHelpShown).isFalse()
        }
    }

    @Test
    fun `Given the cursor help expired When the page mode is shown Then the help is gone but the cursor stays`() {
        val hud = browserHudOf(BrowserLayers(), PageState.Loaded("arte.tv"), isCursorHelpRequested = false)

        assertThat(hud.isCursorVisible).isTrue()
        assertThat(hud.isCursorHelpShown).isFalse()
    }
}

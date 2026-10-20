package com.openctv.browser.features.browser

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.webview.LoadErrorCause
import com.openctv.browser.features.browser.domain.BackAction
import com.openctv.browser.features.browser.domain.BrowserLayers
import com.openctv.browser.features.browser.domain.BrowserMode
import com.openctv.browser.features.browser.domain.PageState
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
}

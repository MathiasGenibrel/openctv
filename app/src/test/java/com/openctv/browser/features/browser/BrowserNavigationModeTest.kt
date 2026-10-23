package com.openctv.browser.features.browser

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.navmode.NavigationMode
import com.openctv.browser.core.webview.LoadErrorCause
import com.openctv.browser.core.webview.SecurityWarning
import com.openctv.browser.core.webview.WebLoadError
import com.openctv.browser.features.browser.domain.BrowserNotice
import com.openctv.browser.features.browser.domain.ElementKey
import com.openctv.browser.features.browser.domain.ElementKeyAction
import com.openctv.browser.features.browser.domain.KeyPhase
import com.openctv.browser.features.browser.domain.PadKey
import com.openctv.browser.features.browser.ui.BrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserNavigationModeTest {
    private val wikipedia = "https://wikipedia.org/"
    private val arte = "https://www.arte.tv/fr/"
    private val bookmarks = FakeBookmarks()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(url: String, modes: FakeNavigationModes = FakeNavigationModes()) =
        BrowserViewModel(url, bookmarks, navigationModes = modes)

    private fun BrowserViewModel.press(key: PadKey, isTextFieldFocused: Boolean = false, isKeyboardShown: Boolean = false) =
        onElementKey(key, KeyPhase.Down, isTextFieldFocused, isKeyboardShown)

    @Test
    fun `Given a site never switched When it opens Then it is in cursor mode with the cursor shown`() {
        val viewModel = viewModel(wikipedia)

        assertThat(viewModel.navigationMode.value).isEqualTo(NavigationMode.Cursor)
        assertThat(viewModel.hud.value.isCursorVisible).isTrue()
        assertThat(viewModel.hud.value.isElementsHelpShown).isFalse()
    }

    @Test
    fun `Given wikipedia in cursor mode When the user switches from the menu Then it is remembered closed and announced`() = runTest {
        val modes = FakeNavigationModes()
        val viewModel = viewModel(wikipedia, modes)
        viewModel.openMenu()

        viewModel.toggleNavigationMode()

        assertThat(viewModel.navigationMode.value).isEqualTo(NavigationMode.Elements)
        assertThat(modes.current).containsExactly("wikipedia.org")
        assertThat(viewModel.layers.value.isMenuOpen).isFalse()
        assertThat(viewModel.notice.value)
            .isEqualTo(BrowserNotice.NavigationModeChanged(NavigationMode.Elements, "wikipedia.org"))
    }

    @Test
    fun `Given wikipedia in elements mode When the user switches back Then the cursor returns and the site is forgotten`() = runTest {
        val modes = FakeNavigationModes("wikipedia.org")
        val viewModel = viewModel(wikipedia, modes)
        backgroundScope.launch { viewModel.hud.collect {} }
        runCurrent()

        viewModel.toggleNavigationMode()
        runCurrent()

        assertThat(viewModel.navigationMode.value).isEqualTo(NavigationMode.Cursor)
        assertThat(modes.current).isEmpty()
        assertThat(viewModel.hud.value.isCursorVisible).isTrue()
        assertThat(viewModel.notice.value)
            .isEqualTo(BrowserNotice.NavigationModeChanged(NavigationMode.Cursor, "wikipedia.org"))
    }

    @Test
    fun `Given wikipedia remembered in elements mode When it is reopened Then it starts in elements mode`() {
        val viewModel = viewModel(wikipedia, FakeNavigationModes("wikipedia.org"))

        assertThat(viewModel.navigationMode.value).isEqualTo(NavigationMode.Elements)
    }

    @Test
    fun `Given a site in elements mode When a link leads to another host Then that host is in cursor mode and back again`() {
        val viewModel = viewModel(wikipedia, FakeNavigationModes("wikipedia.org"))

        viewModel.onPageStarted(arte)
        assertThat(viewModel.navigationMode.value).isEqualTo(NavigationMode.Cursor)

        viewModel.onPageStarted(wikipedia)
        assertThat(viewModel.navigationMode.value).isEqualTo(NavigationMode.Elements)
    }

    @Test
    fun `Given storage fails When the user switches Then the mode is unchanged and a failure notice is shown`() = runTest {
        val modes = FakeNavigationModes().apply { failure = "disk" }
        val viewModel = viewModel(wikipedia, modes)

        viewModel.toggleNavigationMode()

        assertThat(viewModel.navigationMode.value).isEqualTo(NavigationMode.Cursor)
        assertThat(viewModel.notice.value).isEqualTo(BrowserNotice.SettingFailed)
    }

    @Test
    fun `Given elements mode When the page is shown Then the cursor is hidden and the elements help is shown`() {
        val viewModel = viewModel(wikipedia, FakeNavigationModes("wikipedia.org"))

        val hud = viewModel.hud.value

        assertThat(hud.isCursorVisible).isFalse()
        assertThat(hud.isCursorShown).isFalse()
        assertThat(hud.isCursorHelpShown).isFalse()
        assertThat(hud.isElementsHelpShown).isTrue()
    }

    @Test
    fun `Given elements mode When the menu is open Then no key goes to the page`() {
        val viewModel = viewModel(wikipedia, FakeNavigationModes("wikipedia.org"))
        viewModel.openMenu()

        assertThat(viewModel.press(PadKey.Down)).isEqualTo(ElementKeyAction.PassThrough)
        assertThat(viewModel.press(PadKey.Ok)).isEqualTo(ElementKeyAction.PassThrough)
    }

    @Test
    fun `Given elements mode When the address input is open Then no key goes to the page`() {
        val viewModel = viewModel(wikipedia, FakeNavigationModes("wikipedia.org"))
        viewModel.openAddressInput()

        assertThat(viewModel.press(PadKey.Down)).isEqualTo(ElementKeyAction.PassThrough)
    }

    @Test
    fun `Given elements mode When the toolbar has the D-pad Then no key goes to the page`() {
        val viewModel = viewModel(wikipedia, FakeNavigationModes("wikipedia.org"))
        viewModel.enterInterfaceMode()

        assertThat(viewModel.press(PadKey.Down)).isEqualTo(ElementKeyAction.PassThrough)
    }

    @Test
    fun `Given elements mode When an error screen is shown Then the D-pad stays on its buttons`() {
        val viewModel = viewModel(wikipedia, FakeNavigationModes("wikipedia.org"))
        viewModel.onLoadError(WebLoadError(wikipedia, LoadErrorCause.Unknown))

        assertThat(viewModel.press(PadKey.Down)).isEqualTo(ElementKeyAction.PassThrough)
    }

    @Test
    fun `Given cursor mode When the user presses Down Then the cursor keeps the key`() {
        val viewModel = viewModel(arte)

        assertThat(viewModel.press(PadKey.Down)).isEqualTo(ElementKeyAction.PassThrough)
    }

    @Test
    fun `Given elements mode When the user presses Down then OK Then Tab then Enter are asked`() {
        val viewModel = viewModel(wikipedia, FakeNavigationModes("wikipedia.org"))

        assertThat(viewModel.press(PadKey.Down)).isEqualTo(ElementKeyAction.Send(ElementKey.Next))
        assertThat(viewModel.press(PadKey.Ok)).isEqualTo(ElementKeyAction.Send(ElementKey.Activate))
    }

    @Test
    fun `Given elements mode and a warning dismissed When the engine still reports an editor Then OK activates and the next element is trusted again`() {
        val httpUrl = "http://neverssl.com/"
        val viewModel = viewModel(wikipedia, FakeNavigationModes("wikipedia.org"))
        viewModel.onPageStarted(wikipedia)
        viewModel.onUrlChanged(wikipedia, canGoBack = false, canGoForward = false)
        viewModel.onPageFinished(wikipedia)
        viewModel.onLoadError(WebLoadError(httpUrl, LoadErrorCause.Unknown, SecurityWarning.InsecureHttp(httpUrl, "neverssl.com")))
        viewModel.onBack(canGoBack = true)

        // État d'IME périmé : pas de clavier ouvert sur un champ qui n'existe pas.
        assertThat(viewModel.press(PadKey.Ok, isTextFieldFocused = true)).isEqualTo(ElementKeyAction.Send(ElementKey.Activate))
        // Après ce geste sur la page, le moteur est de nouveau cru.
        assertThat(viewModel.press(PadKey.Ok, isTextFieldFocused = true)).isEqualTo(ElementKeyAction.OpenKeyboard)
    }

    @Test
    fun `Given elements mode When Up is held Then the address bar opens and no Shift Tab is sent`() {
        val viewModel = viewModel(wikipedia, FakeNavigationModes("wikipedia.org"))

        val pressed = viewModel.press(PadKey.Up)
        val repeated = viewModel.onElementKey(PadKey.Up, KeyPhase.Repeat, isTextFieldFocused = false)
        val released = viewModel.onElementKey(PadKey.Up, KeyPhase.Up, isTextFieldFocused = false)

        assertThat(pressed).isEqualTo(ElementKeyAction.Ignore)
        assertThat(repeated).isEqualTo(ElementKeyAction.Ignore)
        assertThat(viewModel.layers.value.isInterfaceMode).isTrue()
        assertThat(released).isEqualTo(ElementKeyAction.PassThrough)
    }

    @Test
    fun `Given elements mode When Up is pressed briefly Then the previous element is asked on release and the page keeps the D-pad`() {
        val viewModel = viewModel(wikipedia, FakeNavigationModes("wikipedia.org"))

        viewModel.press(PadKey.Up)
        val released = viewModel.onElementKey(PadKey.Up, KeyPhase.Up, isTextFieldFocused = false)

        assertThat(released).isEqualTo(ElementKeyAction.Send(ElementKey.Previous))
        assertThat(viewModel.layers.value.isInterfaceMode).isFalse()
    }

    @Test
    fun `Given elements mode When a video plays fullscreen Then no key goes to the page`() {
        val viewModel = viewModel(wikipedia, FakeNavigationModes("wikipedia.org"))
        viewModel.onFullscreenShown()

        assertThat(viewModel.press(PadKey.Down)).isEqualTo(ElementKeyAction.PassThrough)
    }

    @Test
    fun `Given elements mode with a text field focused When the keyboard is shown Then Left goes to the field and once closed it goes to the previous element`() {
        val viewModel = viewModel(wikipedia, FakeNavigationModes("wikipedia.org"))

        assertThat(viewModel.press(PadKey.Ok, isTextFieldFocused = true)).isEqualTo(ElementKeyAction.OpenKeyboard)
        val withKeyboard = viewModel.press(PadKey.Left, isTextFieldFocused = true, isKeyboardShown = true)
        val afterBack = viewModel.press(PadKey.Left, isTextFieldFocused = true, isKeyboardShown = false)

        assertThat(withKeyboard).isEqualTo(ElementKeyAction.PassThrough)
        assertThat(afterBack).isEqualTo(ElementKeyAction.Send(ElementKey.Previous))
    }

    @Test
    fun `Given elements mode and a warning dismissed When the system still reports a keyboard Then the arrows still move between elements`() {
        val httpUrl = "http://neverssl.com/"
        val viewModel = viewModel(wikipedia, FakeNavigationModes("wikipedia.org"))
        viewModel.onPageStarted(wikipedia)
        viewModel.onUrlChanged(wikipedia, canGoBack = false, canGoForward = false)
        viewModel.onPageFinished(wikipedia)
        viewModel.onLoadError(WebLoadError(httpUrl, LoadErrorCause.Unknown, SecurityWarning.InsecureHttp(httpUrl, "neverssl.com")))
        viewModel.onBack(canGoBack = true)

        val action = viewModel.press(PadKey.Left, isTextFieldFocused = true, isKeyboardShown = true)

        assertThat(action).isEqualTo(ElementKeyAction.Send(ElementKey.Previous))
    }

    @Test
    fun `Given elements mode When Back is pressed Then it keeps its meaning and the page history goes back`() {
        val viewModel = viewModel(wikipedia, FakeNavigationModes("wikipedia.org"))
        viewModel.openMenu()

        viewModel.onBack(canGoBack = true)

        assertThat(viewModel.layers.value.isMenuOpen).isFalse()
        assertThat(viewModel.navigationMode.value).isEqualTo(NavigationMode.Elements)
    }
}

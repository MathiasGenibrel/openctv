package com.openctv.browser.core.webview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FocusWrapTest {
    private val wrap = FocusWrap(windowMs = 400)

    @Test
    fun `Given Tab pressed on the last element When the engine hands focus back Then Tab is sent again to reach the first element`() {
        wrap.armed(isBackwards = false, nowMs = 1_000)

        assertThat(wrap.onEngineTookFocus(nowMs = 1_050)).isFalse()
    }

    @Test
    fun `Given Shift+Tab pressed on the first element When the engine hands focus back Then Shift+Tab is sent again to reach the last element`() {
        wrap.armed(isBackwards = true, nowMs = 1_000)

        assertThat(wrap.onEngineTookFocus(nowMs = 1_050)).isTrue()
    }

    @Test
    fun `Given a page without focusable element When the wrap itself hands focus back Then nothing is sent again`() {
        wrap.armed(isBackwards = false, nowMs = 1_000)
        wrap.onEngineTookFocus(nowMs = 1_050)

        assertThat(wrap.onEngineTookFocus(nowMs = 1_100)).isNull()
    }

    @Test
    fun `Given no Tab pressed recently When the engine asks for another view Then nothing is sent`() {
        assertThat(wrap.onEngineTookFocus(nowMs = 5_000)).isNull()
        wrap.armed(isBackwards = false, nowMs = 1_000)
        assertThat(wrap.onEngineTookFocus(nowMs = 2_000)).isNull()
    }

    @Test
    fun `Given Tab pressed When the engine asks for another view twice for the same hand-off Then Tab is sent again only once`() {
        wrap.armed(isBackwards = false, nowMs = 1_000)

        assertThat(wrap.onEngineTookFocus(nowMs = 1_050)).isFalse()
        assertThat(wrap.onEngineTookFocus(nowMs = 1_051)).isNull()
    }

    @Test
    fun `Given Tab pressed into a text field When an arrow the field does not use asks for another view Then nothing is sent again`() {
        wrap.armed(isBackwards = false, nowMs = 1_000)

        wrap.onKeyDispatched(isTab = false)

        assertThat(wrap.onEngineTookFocus(nowMs = 1_100)).isNull()
    }

    @Test
    fun `Given Tab pressed When the view receives that Tab itself Then the wrap stays armed`() {
        wrap.armed(isBackwards = true, nowMs = 1_000)

        wrap.onKeyDispatched(isTab = true)

        assertThat(wrap.onEngineTookFocus(nowMs = 1_050)).isTrue()
    }
}

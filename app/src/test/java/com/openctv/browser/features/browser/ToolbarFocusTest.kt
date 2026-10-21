package com.openctv.browser.features.browser

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.features.browser.domain.ReloadAction
import com.openctv.browser.features.browser.domain.ToolbarButton
import com.openctv.browser.features.browser.domain.ToolbarState
import com.openctv.browser.features.browser.domain.focusFallbackOf
import org.junit.Test

class ToolbarFocusTest {

    private fun state(canGoBack: Boolean, canGoForward: Boolean) =
        ToolbarState(canGoBack, canGoForward, ReloadAction.Reload, "arte.tv", isSecure = true, isBookmarked = false)

    @Test
    fun `Given the focus on Forward When the last page is reached and Forward turns inactive Then the focus moves to Back`() {
        val fallback = focusFallbackOf(ToolbarButton.Forward, state(canGoBack = true, canGoForward = false))

        assertThat(fallback).isEqualTo(ToolbarButton.Back)
    }

    @Test
    fun `Given the focus on Forward and no history at all When Forward turns inactive Then the focus moves to Reload`() {
        val fallback = focusFallbackOf(ToolbarButton.Forward, state(canGoBack = false, canGoForward = false))

        assertThat(fallback).isEqualTo(ToolbarButton.Reload)
    }

    @Test
    fun `Given the focus on Back When the first page is reached Then the focus moves to the active Forward`() {
        val fallback = focusFallbackOf(ToolbarButton.Back, state(canGoBack = false, canGoForward = true))

        assertThat(fallback).isEqualTo(ToolbarButton.Forward)
    }

    @Test
    fun `Given the focus on an active button or elsewhere When the toolbar updates Then the focus stays where it is`() {
        assertThat(focusFallbackOf(ToolbarButton.Forward, state(canGoBack = true, canGoForward = true))).isNull()
        assertThat(focusFallbackOf(ToolbarButton.Reload, state(canGoBack = false, canGoForward = false))).isNull()
        assertThat(focusFallbackOf(null, state(canGoBack = false, canGoForward = false))).isNull()
    }
}

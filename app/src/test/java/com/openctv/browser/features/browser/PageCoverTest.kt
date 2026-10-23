package com.openctv.browser.features.browser

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.webview.LoadErrorCause
import com.openctv.browser.features.browser.domain.PageCover
import com.openctv.browser.features.browser.domain.PageState
import org.junit.Test

class PageCoverTest {
    @Test
    fun `Given a security warning covers the page When it disappears Then the page takes the focus back`() {
        assertThat(PageCover.shouldRestoreFocus(wasCovering = true, isCovering = false)).isTrue()
    }

    @Test
    fun `Given the page is shown When nothing covers it Then the focus is not touched`() {
        assertThat(PageCover.shouldRestoreFocus(wasCovering = false, isCovering = false)).isFalse()
    }

    @Test
    fun `Given the page is shown When an error covers it Then the error screen takes the focus itself`() {
        assertThat(PageCover.shouldRestoreFocus(wasCovering = false, isCovering = true)).isFalse()
    }

    @Test
    fun `Given a page state When it is an error Then it covers the page and loading or loaded states do not`() {
        assertThat(PageCover.isCovering(PageState.Error("example.org", LoadErrorCause.Unreachable))).isTrue()
        assertThat(PageCover.isCovering(PageState.Loading("example.org", 0.2f))).isFalse()
        assertThat(PageCover.isCovering(PageState.Loaded("example.org"))).isFalse()
    }
}

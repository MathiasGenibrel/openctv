package com.openctv.browser.core.webview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageScrollTest {

    @Test
    fun `Given a 1080 pixel high screen When scrolling one screen Then the page moves by 90 percent of it`() {
        assertThat(pageScrollPixels(1080, ScrollDirection.Down)).isEqualTo(972)
        assertThat(pageScrollPixels(1080, ScrollDirection.Up)).isEqualTo(-972)
    }
}

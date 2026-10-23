package com.openctv.browser.core.designsystem

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BreakableHostTest {
    @Test
    fun `Given a long host When it is displayed Then it can only break after a dot`() {
        val shown = breakableHost("gitlab-dashboard.tools.internal")

        assertThat(shown).isEqualTo("gitlab-dashboard.​tools.​internal")
        assertThat(shown.replace("​", "")).isEqualTo("gitlab-dashboard.tools.internal")
    }

    @Test
    fun `Given a host without dots When it is displayed Then it is unchanged`() {
        assertThat(breakableHost("localhost")).isEqualTo("localhost")
    }
}

package com.openctv.browser.core.designsystem

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OpeningKeyGuardTest {

    private val ok = 23L
    private val down = 20L

    @Test
    fun `Given a menu opened by holding OK When OK keeps repeating then is released Then nothing reaches the menu`() {
        val guard = OpeningKeyGuard()

        val repeat = guard.swallows(ok, isKeyDown = true, repeatCount = 5)
        val release = guard.swallows(ok, isKeyDown = false, repeatCount = 0)

        assertThat(repeat).isTrue()
        assertThat(release).isTrue()
    }

    @Test
    fun `Given an open menu When the user presses then releases OK on a button Then the click goes through`() {
        val guard = OpeningKeyGuard()
        guard.swallows(ok, isKeyDown = false, repeatCount = 0)

        val press = guard.swallows(ok, isKeyDown = true, repeatCount = 0)
        val release = guard.swallows(ok, isKeyDown = false, repeatCount = 0)

        assertThat(press).isFalse()
        assertThat(release).isFalse()
    }

    @Test
    fun `Given an open menu When the user holds Down to move between buttons Then every repeat moves the focus`() {
        val guard = OpeningKeyGuard()

        val press = guard.swallows(down, isKeyDown = true, repeatCount = 0)
        val repeat = guard.swallows(down, isKeyDown = true, repeatCount = 1)
        val release = guard.swallows(down, isKeyDown = false, repeatCount = 0)

        assertThat(listOf(press, repeat, release)).containsExactly(false, false, false)
    }
}

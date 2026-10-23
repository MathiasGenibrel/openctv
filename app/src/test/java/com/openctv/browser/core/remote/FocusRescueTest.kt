package com.openctv.browser.core.remote

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FocusRescueTest {
    private val rescue = FocusRescue()

    private class FakeRescuer(var isLost: Boolean, var canRestore: Boolean = true) : PageFocusRescuer {
        var restoreCount = 0
        override fun isFocusLost() = isLost
        override fun restoreFocus(): Boolean {
            restoreCount++
            return canRestore
        }
    }

    @Test
    fun `Given no element has the focus When the Search key is not handled Then the page gets the focus back and the key is replayed`() {
        val rescuer = FakeRescuer(isLost = true)
        rescue.register(rescuer)
        var replays = 0

        val consumed = rescue.onUnhandledKey(KeyEvent.KEYCODE_SEARCH, isKeyDown = true) { replays++; true }

        assertThat(consumed).isTrue()
        assertThat(rescuer.restoreCount).isEqualTo(1)
        assertThat(replays).isEqualTo(1)
    }

    @Test
    fun `Given no element has the focus When OK is not handled Then the focus comes back without activating anything`() {
        val activationKeys = listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)

        activationKeys.forEach { key ->
            val rescuer = FakeRescuer(isLost = true)
            val localRescue = FocusRescue().also { it.register(rescuer) }
            var replays = 0

            val consumed = localRescue.onUnhandledKey(key, isKeyDown = true) { replays++; true }

            assertThat(consumed).isTrue()
            assertThat(rescuer.restoreCount).isEqualTo(1)
            assertThat(replays).isEqualTo(0)
        }
    }

    @Test
    fun `Given no element has the focus When a D-pad arrow is not handled Then it is replayed on the restored focus`() {
        rescue.register(FakeRescuer(isLost = true))
        var replays = 0

        val consumed = rescue.onUnhandledKey(KeyEvent.KEYCODE_DPAD_DOWN, isKeyDown = true) { replays++; true }

        assertThat(consumed).isTrue()
        assertThat(replays).isEqualTo(1)
    }

    @Test
    fun `Given no element has the focus When a digit is not handled Then it is replayed once the page has the focus`() {
        rescue.register(FakeRescuer(isLost = true))

        val consumed = rescue.onUnhandledKey(KeyEvent.KEYCODE_3, isKeyDown = true) { true }

        assertThat(consumed).isTrue()
    }

    @Test
    fun `Given the focus is somewhere When a D-pad key is not handled Then the focus is left alone`() {
        val rescuer = FakeRescuer(isLost = false)
        rescue.register(rescuer)

        val consumed = rescue.onUnhandledKey(KeyEvent.KEYCODE_DPAD_LEFT, isKeyDown = true) { true }

        assertThat(consumed).isFalse()
        assertThat(rescuer.restoreCount).isEqualTo(0)
    }

    @Test
    fun `Given an overlay owns the focus When a digit is not handled Then nothing is replayed`() {
        rescue.register(FakeRescuer(isLost = true, canRestore = false))
        var replays = 0

        val consumed = rescue.onUnhandledKey(KeyEvent.KEYCODE_1, isKeyDown = true) { replays++; true }

        assertThat(consumed).isFalse()
        assertThat(replays).isEqualTo(0)
    }

    @Test
    fun `Given no screen registered When the Search key is not handled Then it never reaches the system assistant`() {
        assertThat(rescue.onUnhandledKey(KeyEvent.KEYCODE_SEARCH, isKeyDown = true) { false }).isTrue()
        assertThat(rescue.onUnhandledKey(KeyEvent.KEYCODE_SEARCH, isKeyDown = false) { false }).isTrue()
    }

    @Test
    fun `Given the focus is lost When a system key arrives Then it is never intercepted`() {
        val rescuer = FakeRescuer(isLost = true)
        rescue.register(rescuer)
        val systemKeys = listOf(
            KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_ASSIST, KeyEvent.KEYCODE_VOICE_ASSIST,
            KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_GUIDE,
        )

        systemKeys.forEach { key ->
            assertThat(rescue.onUnhandledKey(key, isKeyDown = true) { true }).isFalse()
        }
        assertThat(rescuer.restoreCount).isEqualTo(0)
    }

    @Test
    fun `Given the screen left When it unregisters Then keys are no longer rescued`() {
        val rescuer = FakeRescuer(isLost = true)
        val unregister = rescue.register(rescuer)
        unregister()

        assertThat(rescue.onUnhandledKey(KeyEvent.KEYCODE_DPAD_DOWN, isKeyDown = true) { true }).isFalse()
        assertThat(rescuer.restoreCount).isEqualTo(0)
    }
}

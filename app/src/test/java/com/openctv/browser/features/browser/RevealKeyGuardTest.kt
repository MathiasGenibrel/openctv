package com.openctv.browser.features.browser

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.features.browser.domain.KeyPhase
import com.openctv.browser.features.browser.domain.RemoteKey
import com.openctv.browser.features.browser.domain.RevealKeyGuard
import org.junit.Test

class RevealKeyGuardTest {

    private val guard = RevealKeyGuard()

    private fun key(phase: KeyPhase, code: Long = OK) = RemoteKey(code, phase)

    @Test
    fun `Given a visible HUD When the user presses and releases a key Then nothing is swallowed`() {
        val swallowed = listOf(
            guard.swallows(key(KeyPhase.Down), isHudHidden = false),
            guard.swallows(key(KeyPhase.Repeat), isHudHidden = false),
            guard.swallows(key(KeyPhase.Up), isHudHidden = false),
        )

        assertThat(swallowed).containsExactly(false, false, false)
    }

    @Test
    fun `Given a hidden HUD When the user presses Back Then Back keeps its meaning`() {
        val back = RemoteKey(BACK, KeyPhase.Down, isBack = true)

        assertThat(guard.swallows(back, isHudHidden = true)).isFalse()
    }

    @Test
    fun `Given a key held before the HUD hid When it is released while hidden Then the release goes through`() {
        // Le curseur a reçu l'appui : il doit recevoir le relâchement pour ne pas rester « enfoncé ».
        assertThat(guard.swallows(key(KeyPhase.Up, RIGHT), isHudHidden = true)).isFalse()
    }

    @Test
    fun `Given the release of the reveal key was lost When the same key is pressed again Then the new press goes through`() {
        guard.swallows(key(KeyPhase.Down), isHudHidden = true)

        val isNewPressSwallowed = guard.swallows(key(KeyPhase.Down), isHudHidden = false)
        val isItsReleaseSwallowed = guard.swallows(key(KeyPhase.Up), isHudHidden = false)

        assertThat(isNewPressSwallowed).isFalse()
        assertThat(isItsReleaseSwallowed).isFalse()
    }

    private companion object {
        const val BACK = 4L
        const val OK = 23L
        const val RIGHT = 22L
    }
}

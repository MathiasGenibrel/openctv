package com.openctv.baselineprofile

import android.view.KeyEvent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test

/**
 * Génère le Baseline Profile d'OpenCTV en rejouant, à la télécommande (D-pad + OK + Menu + Retour), le parcours
 * le plus fréquent : démarrage à froid -> accueil -> saisie -> page -> curseur -> barre et menu -> réglages -> Retour.
 * À lancer sur la TV : `./gradlew :app:generateBaselineProfile` (voir CLAUDE.md).
 *
 * Les touches sont espacées : l'interface TV anime ses changements de focus, une rafale serait perdue.
 */
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        rule.collect(
            packageName = TARGET_PACKAGE,
            maxIterations = 3,
            stableIterations = 1,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            device.settle(LONG_WAIT_MS)

            // Accueil -> réglages -> retour à l'accueil.
            device.press(KeyEvent.KEYCODE_DPAD_UP)
            device.press(KeyEvent.KEYCODE_DPAD_RIGHT)
            device.press(KeyEvent.KEYCODE_DPAD_CENTER)
            device.settle(MEDIUM_WAIT_MS)
            device.press(KeyEvent.KEYCODE_DPAD_DOWN)
            device.press(KeyEvent.KEYCODE_DPAD_DOWN)
            device.press(KeyEvent.KEYCODE_BACK)
            device.settle(MEDIUM_WAIT_MS)

            // Accueil -> saisie -> page.
            device.press(KeyEvent.KEYCODE_DPAD_DOWN)
            device.press(KeyEvent.KEYCODE_DPAD_CENTER)
            device.settle(MEDIUM_WAIT_MS)
            PAGE_ADDRESS.forEach { device.press(keyCodeOf(it)) }
            device.press(KeyEvent.KEYCODE_ENTER)
            device.settle(PAGE_LOAD_WAIT_MS)

            // Curseur : déplacements dans toutes les directions.
            repeat(CURSOR_STEPS) { device.press(KeyEvent.KEYCODE_DPAD_RIGHT, SHORT_WAIT_MS) }
            repeat(CURSOR_STEPS) { device.press(KeyEvent.KEYCODE_DPAD_DOWN, SHORT_WAIT_MS) }
            repeat(CURSOR_STEPS) { device.press(KeyEvent.KEYCODE_DPAD_LEFT, SHORT_WAIT_MS) }
            repeat(CURSOR_STEPS) { device.press(KeyEvent.KEYCODE_DPAD_UP, SHORT_WAIT_MS) }
            device.press(KeyEvent.KEYCODE_DPAD_CENTER)

            // Barre d'outils puis menu de la page. Interface masquée : le premier appui ne fait que la réafficher.
            device.press(KeyEvent.KEYCODE_MENU)
            device.press(KeyEvent.KEYCODE_MENU)
            repeat(TOOLBAR_STEPS_TO_MENU) { device.press(KeyEvent.KEYCODE_DPAD_RIGHT) }
            device.press(KeyEvent.KEYCODE_DPAD_CENTER)
            device.settle(MEDIUM_WAIT_MS)
            device.press(KeyEvent.KEYCODE_DPAD_DOWN)
            device.press(KeyEvent.KEYCODE_BACK)
            device.settle(MEDIUM_WAIT_MS)

            // Retour : page -> accueil.
            device.press(KeyEvent.KEYCODE_BACK)
            device.press(KeyEvent.KEYCODE_BACK)
            device.settle(MEDIUM_WAIT_MS)
        }
    }

    private fun UiDevice.press(keyCode: Int, waitMs: Long = KEY_WAIT_MS) {
        pressKeyCode(keyCode)
        Thread.sleep(waitMs)
    }

    private fun UiDevice.settle(waitMs: Long) {
        waitForIdle()
        Thread.sleep(waitMs)
    }

    private fun keyCodeOf(char: Char): Int = when (char) {
        '.' -> KeyEvent.KEYCODE_PERIOD
        else -> KeyEvent.keyCodeFromString("KEYCODE_${char.uppercaseChar()}")
    }

    private companion object {
        const val TARGET_PACKAGE = "com.openctv.browser"
        const val PAGE_ADDRESS = "example.org"
        const val CURSOR_STEPS = 12
        const val TOOLBAR_STEPS_TO_MENU = 2
        const val KEY_WAIT_MS = 700L
        const val SHORT_WAIT_MS = 120L
        const val MEDIUM_WAIT_MS = 2_000L
        const val LONG_WAIT_MS = 4_000L
        const val PAGE_LOAD_WAIT_MS = 8_000L
    }
}

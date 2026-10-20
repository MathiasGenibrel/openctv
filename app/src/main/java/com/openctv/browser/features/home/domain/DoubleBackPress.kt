package com.openctv.browser.features.home.domain

import java.time.Clock
import java.time.Duration
import java.time.Instant

enum class BackPressOutcome { ShowExitHint, Exit }

/**
 * Sortie de l'app par double appui sur Retour : le premier appui prévient, le second quitte
 * s'il arrive dans [window]. Jamais de sortie par surprise.
 */
class DoubleBackPress(
    private val clock: Clock = Clock.systemUTC(),
    /** Délai pendant lequel un second appui quitte ; c'est aussi la durée d'affichage de l'avertissement. */
    val window: Duration = Duration.ofSeconds(2),
) {
    private var lastPress: Instant? = null

    fun onBackPressed(): BackPressOutcome {
        val now = clock.instant()
        val previous = lastPress
        val isSecondPressInTime = previous != null && Duration.between(previous, now) <= window
        return if (isSecondPressInTime) {
            lastPress = null
            BackPressOutcome.Exit
        } else {
            lastPress = now
            BackPressOutcome.ShowExitHint
        }
    }
}

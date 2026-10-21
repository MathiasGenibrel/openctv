package com.openctv.browser.core.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DestinationTest {

    private fun roundTrip(destination: Destination) = Destination.decode(Destination.encode(destination))

    @Test
    fun `Given a page whose url contains colons When the activity is recreated Then the same page is restored`() {
        val browser = Destination.Browser("https://example.org:8080/a?b=c:d")

        assertThat(roundTrip(browser)).isEqualTo(browser)
        assertThat(roundTrip(Destination.Settings)).isEqualTo(Destination.Settings)
        assertThat(roundTrip(Destination.History())).isEqualTo(Destination.History())
    }

    @Test
    fun `Given a page reopened from the history When the activity is recreated Then it still returns to that entry`() {
        val browser = Destination.Browser("https://example.org:8080/a?b=c:d", openedFromHistoryEntry = 42)
        val history = Destination.History(focusedEntryId = 42)

        assertThat(roundTrip(browser)).isEqualTo(browser)
        assertThat(roundTrip(history)).isEqualTo(history)
    }

    @Test
    fun `Given a page opened from the history When Back leaves the browser Then it returns to the history on that entry`() {
        val fromHistory = Destination.Browser("https://www.arte.tv/", openedFromHistoryEntry = 7)
        val fromHome = Destination.Browser("https://www.arte.tv/")

        assertThat(fromHistory.returnTo).isEqualTo(Destination.History(focusedEntryId = 7))
        assertThat(fromHome.returnTo).isEqualTo(Destination.Home)
    }

    @Test
    fun `Given a token saved while the search was a destination When restored Then the user lands on the home`() {
        assertThat(Destination.decode("search:Browse:Browser:https://arte.tv")).isEqualTo(Destination.Home)
        assertThat(Destination.decode("unknown")).isEqualTo(Destination.Home)
    }
}

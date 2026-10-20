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
    }

    @Test
    fun `Given a token saved while the search was a destination When restored Then the user lands on the home`() {
        assertThat(Destination.decode("search:Browse:Browser:https://arte.tv")).isEqualTo(Destination.Home)
        assertThat(Destination.decode("unknown")).isEqualTo(Destination.Home)
    }
}

package com.openctv.browser.core.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DestinationTest {

    private fun roundTrip(destination: Destination) = Destination.decode(Destination.encode(destination))

    @Test
    fun `Given a search with a prefilled url containing colons When the activity is recreated Then it is restored intact`() {
        val search = Destination.Search(SearchMode.Browse, "https://example.org:8080/a?b=c:d", SearchOrigin.Browser)

        assertThat(roundTrip(search)).isEqualTo(search)
    }

    @Test
    fun `Given the add bookmark search When the activity is recreated Then mode and origin are restored`() {
        val search = Destination.Search(SearchMode.AddBookmark)

        assertThat(roundTrip(search)).isEqualTo(search)
    }

    @Test
    fun `Given a corrupted search token When restored Then the user lands on the home`() {
        assertThat(Destination.decode("search:Nope:Home:x")).isEqualTo(Destination.Home)
        assertThat(Destination.decode("search:Browse")).isEqualTo(Destination.Home)
    }

    @Test
    fun `Given a search opened from a page When it is closed Then the user returns to that page`() {
        val search = Destination.Search(SearchMode.Browse, "https://arte.tv", SearchOrigin.Browser)

        assertThat(search.backTarget()).isEqualTo(Destination.Browser("https://arte.tv"))
        assertThat(Destination.Search().backTarget()).isEqualTo(Destination.Home)
    }
}

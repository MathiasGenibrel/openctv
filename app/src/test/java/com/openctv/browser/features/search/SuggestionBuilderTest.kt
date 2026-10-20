package com.openctv.browser.features.search

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.features.bookmarks.domain.Bookmark
import com.openctv.browser.features.search.domain.RecentSearchList
import com.openctv.browser.features.search.domain.Suggestion
import com.openctv.browser.features.search.domain.SuggestionBuilder
import java.time.Instant
import org.junit.Test

class SuggestionBuilderTest {

    private val arte = Bookmark("https://arte.tv", "Arte", Instant.EPOCH)
    private val wiki = Bookmark("https://www.wikipedia.org", "Wikipédia", Instant.EPOCH)

    @Test
    fun `Given arte is a favorite When the user types arte Then arte is proposed as a favorite`() {
        val result = SuggestionBuilder.build("arte", listOf(arte, wiki), recents = emptyList())

        assertThat(result).containsExactly(Suggestion.Favorite("https://arte.tv", "Arte"))
    }

    @Test
    fun `Given a favorite When the typed text matches only its address Then it is still proposed`() {
        val result = SuggestionBuilder.build("WIKIPEDIA.org", listOf(wiki), recents = emptyList())

        assertThat(result).containsExactly(Suggestion.Favorite("https://www.wikipedia.org", "Wikipédia"))
    }

    @Test
    fun `Given favorites and recent searches When the user types Then favorites come first then matching recents`() {
        val result = SuggestionBuilder.build("arte", listOf(arte), recents = listOf("meteo", "arte documentaire"))

        assertThat(result).containsExactly(
            Suggestion.Favorite("https://arte.tv", "Arte"),
            Suggestion.Recent("arte documentaire"),
        ).inOrder()
    }

    @Test
    fun `Given an empty input When suggestions are built Then only recent searches are offered`() {
        val recents = listOf("a", "b", "c", "d", "e", "f", "g")

        val result = SuggestionBuilder.build("  ", listOf(arte), recents)

        assertThat(result).hasSize(SuggestionBuilder.MaxRecents)
        assertThat(result.first()).isEqualTo(Suggestion.Recent("a"))
    }

    @Test
    fun `Given a recent search identical to the input When suggestions are built Then it is not repeated`() {
        val result = SuggestionBuilder.build("Meteo", emptyList(), recents = listOf("meteo", "meteo paris"))

        assertThat(result).containsExactly(Suggestion.Recent("meteo paris"))
    }

    @Test
    fun `Given a favorite without title When suggested Then its domain is displayed`() {
        val untitled = Bookmark("https://www.arte.tv/fr", "", Instant.EPOCH)

        val result = SuggestionBuilder.build("arte", listOf(untitled), emptyList())

        assertThat(result).containsExactly(Suggestion.Favorite("https://www.arte.tv/fr", "arte.tv"))
    }

    @Test
    fun `Given a repeated search in another case When recorded Then it moves to the top without duplicate`() {
        val result = RecentSearchList.add(listOf("meteo", "arte"), "  ARTE ")

        assertThat(result).containsExactly("ARTE", "meteo").inOrder()
    }

    @Test
    fun `Given a full history When a new search is recorded Then the oldest one is dropped`() {
        val full = (1..RecentSearchList.MaxSize).map { "q$it" }

        val result = RecentSearchList.add(full, "new")

        assertThat(result).hasSize(RecentSearchList.MaxSize)
        assertThat(result.first()).isEqualTo("new")
        assertThat(result).doesNotContain("q${RecentSearchList.MaxSize}")
    }

    @Test
    fun `Given a blank search When recorded Then the history is unchanged`() {
        assertThat(RecentSearchList.add(listOf("a"), "   ")).containsExactly("a")
    }
}

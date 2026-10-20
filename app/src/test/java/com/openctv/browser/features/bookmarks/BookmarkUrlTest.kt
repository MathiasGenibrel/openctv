package com.openctv.browser.features.bookmarks

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.features.bookmarks.domain.BookmarkUrl
import org.junit.Test

class BookmarkUrlTest {

    @Test
    fun `Given the same site written with different case, slash and fragment When normalized Then all give one key`() {
        val variants = listOf(
            "https://arte.tv",
            "https://arte.tv/",
            "  HTTPS://Arte.TV/#top ",
        )

        val keys = variants.map(BookmarkUrl::normalize).toSet()

        assertThat(keys).containsExactly("https://arte.tv")
    }

    @Test
    fun `Given two different pages of a site When normalized Then they stay distinct`() {
        val home = BookmarkUrl.normalize("https://arte.tv/fr/")
        val other = BookmarkUrl.normalize("https://arte.tv/fr/videos/?page=2")

        assertThat(home).isEqualTo("https://arte.tv/fr")
        assertThat(other).isEqualTo("https://arte.tv/fr/videos?page=2")
    }

    @Test
    fun `Given a string that is not a valid url When normalized Then it is only trimmed`() {
        assertThat(BookmarkUrl.normalize("  not a url ")).isEqualTo("not a url")
    }
}

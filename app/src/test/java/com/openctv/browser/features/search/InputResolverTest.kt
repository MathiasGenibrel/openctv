package com.openctv.browser.features.search

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.features.search.domain.InputResolver
import com.openctv.browser.features.search.domain.ResolvedInput
import com.openctv.browser.features.settings.domain.SearchEngine
import org.junit.Test

class InputResolverTest {

    private fun resolve(input: String, engine: SearchEngine = SearchEngine.DuckDuckGo) =
        InputResolver.resolve(input, engine)

    @Test
    fun `Given DuckDuckGo When the user validates a bare domain Then the browser opens it in https`() {
        assertThat(resolve("arte.tv")).isEqualTo(ResolvedInput.Url("https://arte.tv"))
    }

    @Test
    fun `Given a domain with a path and query When validated Then the path and query are kept`() {
        assertThat(resolve("www.arte.tv/fr/videos?q=1")).isEqualTo(ResolvedInput.Url("https://www.arte.tv/fr/videos?q=1"))
    }

    @Test
    fun `Given a full web address When validated Then it is opened as typed`() {
        assertThat(resolve("https://example.org/a b".replace(" ", "%20")))
            .isEqualTo(ResolvedInput.Url("https://example.org/a%20b"))
        assertThat(resolve("HTTP://example.org")).isEqualTo(ResolvedInput.Url("HTTP://example.org"))
    }

    @Test
    fun `Given a local address When validated Then it is opened in http`() {
        assertThat(resolve("localhost:8080")).isEqualTo(ResolvedInput.Url("http://localhost:8080"))
        assertThat(resolve("localhost")).isEqualTo(ResolvedInput.Url("http://localhost"))
        assertThat(resolve("192.168.1.10:3000/admin")).isEqualTo(ResolvedInput.Url("http://192.168.1.10:3000/admin"))
        assertThat(resolve("http://intranet/")).isEqualTo(ResolvedInput.Url("http://intranet/"))
    }

    @Test
    fun `Given Google When the user validates words Then the browser opens the Google search`() {
        val result = resolve("arte documentaire", SearchEngine.Google)

        assertThat(result).isEqualTo(
            ResolvedInput.Search("arte documentaire", "https://www.google.com/search?q=arte+documentaire"),
        )
    }

    @Test
    fun `Given a single word without extension When validated Then it is a search`() {
        assertThat(resolve("arte")).isInstanceOf(ResolvedInput.Search::class.java)
    }

    @Test
    fun `Given text that only looks like an address When validated Then it is a search`() {
        listOf("3.14", "999.1.1.1", "arte.tv rocks", "foo.123", "localhost:99999", "-a.com", "a..com").forEach {
            assertThat(resolve(it)).isInstanceOf(ResolvedInput.Search::class.java)
        }
    }

    @Test
    fun `Given a dangerous scheme When validated Then it is never opened and becomes a search`() {
        listOf(
            "javascript:alert(1)", "file:///etc/passwd", "intent://scan#Intent;end", "market://details?id=x",
            "data:text/html,hi", "ftp://example.org", "mailto:me@example.org",
        ).forEach {
            val result = resolve(it)
            assertThat(result).isInstanceOf(ResolvedInput.Search::class.java)
            assertThat(result!!.url).startsWith("https://duckduckgo.com/?q=")
        }
    }

    @Test
    fun `Given an address with credentials When validated Then it is a search, not a spoofable address`() {
        assertThat(resolve("https://arte.tv@evil.example")).isInstanceOf(ResolvedInput.Search::class.java)
    }

    @Test
    fun `Given blank input When validated Then nothing is resolved`() {
        assertThat(resolve("   ")).isNull()
    }

    @Test
    fun `Given a query with special characters When validated Then the search url is encoded`() {
        val result = resolve("café & thé", SearchEngine.Qwant)

        assertThat(result!!.url).isEqualTo("https://www.qwant.com/?q=caf%C3%A9+%26+th%C3%A9")
    }
}

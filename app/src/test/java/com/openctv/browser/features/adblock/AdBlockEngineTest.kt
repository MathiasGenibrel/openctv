package com.openctv.browser.features.adblock

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.features.adblock.data.FilterListParser
import com.openctv.browser.features.adblock.domain.AdBlockEngine
import com.openctv.browser.features.adblock.domain.CompiledFilters
import com.openctv.browser.features.adblock.domain.DefaultAdBlockEngine
import org.junit.Test

class AdBlockEngineTest {

    private fun engineWith(list: String): AdBlockEngine =
        DefaultAdBlockEngine().apply { updateFilters(FilterListParser.parse(list)) }

    @Test
    fun `Given a blocked domain When a page requests it Then the request is blocked`() {
        val engine = engineWith("||ads.example.com^")
        assertThat(engine.shouldBlock("https://ads.example.com/banner.js", "https://news.org/")).isTrue()
    }

    @Test
    fun `Given a blocked domain When a page requests one of its subdomains Then the request is blocked`() {
        val engine = engineWith("||ads.example.com^")
        assertThat(engine.shouldBlock("https://cdn.ads.example.com/banner.js", "https://news.org/")).isTrue()
    }

    @Test
    fun `Given a blocked subdomain When a page requests the parent domain Then the request is allowed`() {
        val engine = engineWith("||ads.example.com^")
        assertThat(engine.shouldBlock("https://example.com/app.js", "https://example.com/")).isFalse()
        assertThat(engine.shouldBlock("https://notads.example.com/x", "https://example.com/")).isFalse()
        assertThat(engine.shouldBlock("https://badads.example.com.evil.org/x", null)).isFalse()
    }

    @Test
    fun `Given a blocked domain and an exception rule When a page requests the excepted subdomain Then it is allowed`() {
        val engine = engineWith("||example.com^\n@@||static.example.com^")
        assertThat(engine.shouldBlock("https://static.example.com/lib.js", "https://news.org/")).isFalse()
        assertThat(engine.shouldBlock("https://ads.example.com/a.js", "https://news.org/")).isTrue()
    }

    @Test
    fun `Given an exception listed before the block rule When deciding Then the exception still wins`() {
        val engine = engineWith("@@||ads.example.com^\n||ads.example.com^")
        assertThat(engine.shouldBlock("https://ads.example.com/a.js", "https://news.org/")).isFalse()
    }

    @Test
    fun `Given the blocker disabled for a site When that site requests a blocked domain Then it is allowed`() {
        val engine = engineWith("||ads.example.com^")
        engine.setEnabledForSite("arte.tv", enabled = false)

        assertThat(engine.shouldBlock("https://ads.example.com/a.js", "https://www.arte.tv/fr/")).isFalse()
        assertThat(engine.shouldBlock("https://ads.example.com/a.js", "https://news.org/")).isTrue()
    }

    @Test
    fun `Given a site disabled then re-enabled When it requests a blocked domain Then it is blocked again`() {
        val engine = engineWith("||ads.example.com^")
        engine.setEnabledForSite("arte.tv", enabled = false)
        engine.setEnabledForSite("arte.tv", enabled = true)

        assertThat(engine.isEnabledForSite("arte.tv")).isTrue()
        assertThat(engine.shouldBlock("https://ads.example.com/a.js", "https://arte.tv/")).isTrue()
    }

    @Test
    fun `Given an empty list When any request is made Then nothing is blocked`() {
        val engine = DefaultAdBlockEngine()
        assertThat(engine.shouldBlock("https://ads.example.com/a.js", "https://news.org/")).isFalse()

        engine.updateFilters(CompiledFilters.EMPTY)
        assertThat(engine.shouldBlock("https://ads.example.com/a.js", "https://news.org/")).isFalse()
    }

    @Test
    fun `Given a third-party only rule When the page is on the same site Then the request is allowed`() {
        val engine = engineWith("||tracker.example.com^\$third-party")
        assertThat(engine.shouldBlock("https://tracker.example.com/t.js", "https://www.example.com/")).isFalse()
        assertThat(engine.shouldBlock("https://tracker.example.com/t.js", "https://news.org/")).isTrue()
    }

    @Test
    fun `Given a domain option When the page is outside the listed domains Then the request is allowed`() {
        val engine = engineWith("||ads.example.com^\$domain=news.org|~live.news.org")
        assertThat(engine.shouldBlock("https://ads.example.com/a", "https://www.news.org/")).isTrue()
        assertThat(engine.shouldBlock("https://ads.example.com/a", "https://live.news.org/")).isFalse()
        assertThat(engine.shouldBlock("https://ads.example.com/a", "https://other.org/")).isFalse()
    }

    @Test
    fun `Given non web URLs When deciding Then nothing is blocked and nothing crashes`() {
        val engine = engineWith("||example.com^")
        assertThat(engine.shouldBlock("intent://example.com/x", null)).isFalse()
        assertThat(engine.shouldBlock("not a url", null)).isFalse()
        assertThat(engine.shouldBlock("https://", null)).isFalse()
    }

    @Test
    fun `Given a large list When making 100000 decisions Then the average stays far below a millisecond`() {
        val rules = (0 until 50_000).joinToString("\n") { "||ads$it.tracker$it.com^" }
        val engine = engineWith(rules)
        val urls = listOf(
            "https://cdn.ads42.tracker42.com/a.js",
            "https://www.wikipedia.org/wiki/Test",
            "https://static.some-site.example.co.uk/img.png",
        )
        repeat(20_000) { engine.shouldBlock(urls[it % 3], "https://news.org/") } // warm-up JIT

        val start = System.nanoTime()
        var blocked = 0
        repeat(100_000) { if (engine.shouldBlock(urls[it % 3], "https://news.org/")) blocked++ }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertThat(blocked).isGreaterThan(0)
        assertThat(elapsedMs).isLessThan(2_000) // seuil très large (20 µs/décision) : anti-régression, non flaky
    }
}

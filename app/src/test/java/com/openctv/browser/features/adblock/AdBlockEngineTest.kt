package com.openctv.browser.features.adblock

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.features.adblock.data.CookieListParser
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
        engine.updateDisabledSites(setOf("arte.tv"))

        assertThat(engine.shouldBlock("https://ads.example.com/a.js", "https://www.arte.tv/fr/")).isFalse()
        assertThat(engine.shouldBlock("https://ads.example.com/a.js", "https://news.org/")).isTrue()
    }

    @Test
    fun `Given a site disabled then re-enabled When it requests a blocked domain Then it is blocked again`() {
        val engine = engineWith("||ads.example.com^")
        engine.updateDisabledSites(setOf("arte.tv"))
        engine.updateDisabledSites(emptySet())

        assertThat(engine.isEnabledForSite("arte.tv")).isTrue()
        assertThat(engine.shouldBlock("https://ads.example.com/a.js", "https://arte.tv/")).isTrue()
    }

    @Test
    fun `Given disabled sites restored from storage as urls or mixed case When deciding Then those sites are allowed`() {
        val engine = engineWith("||ads.example.com^")
        engine.updateDisabledSites(listOf("https://WWW.Arte.tv/fr/", " News.org. ", ""))

        assertThat(engine.isEnabledForSite("www.arte.tv")).isFalse()
        assertThat(engine.isEnabledForSite("arte.tv")).isTrue()
        assertThat(engine.shouldBlock("https://ads.example.com/a.js", "https://live.news.org/")).isFalse()
        assertThat(engine.shouldBlock("https://ads.example.com/a.js", "https://other.org/")).isTrue()
    }

    @Test
    fun `Given request urls with credentials port case or trailing dot When deciding Then the host is still matched`() {
        val engine = engineWith("||ads.example.com^")
        assertThat(engine.shouldBlock("HTTPS://user:pw@Ads.Example.COM.:8443/a.js?x=1", null)).isTrue()
        assertThat(engine.shouldBlock("http://ads.example.com#frag", null)).isTrue()
        assertThat(engine.shouldBlock("https://ads.example.com@evil.org/a.js", null)).isFalse()
        assertThat(engine.shouldBlock("https://[::1]/a.js", null)).isFalse()
        assertThat(engine.shouldBlock("ftp://ads.example.com/a.js", null)).isFalse()
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

class CookieBannerEngineTest {

    private val cookieList = CookieListParser.parse(
        """
        ||cmp.example.net^
        ##.cookie-banner
        news.example.org##.cmp-wall
        """.trimIndent().lineSequence(),
    )

    private val engine: AdBlockEngine = DefaultAdBlockEngine().apply {
        updateFilters(FilterListParser.parse("||ads.example.com^"))
        updateCookieFilters(cookieList.network, cookieList.cosmetic)
    }

    private val page = "https://news.example.org/article"

    @Test
    fun `Given the cookie list When a page loads a consent script Then it is blocked`() {
        assertThat(engine.shouldBlock("https://cmp.example.net/consent.js", page)).isTrue()
        assertThat(engine.shouldBlock("https://cdn.cmp.example.net/consent.js", page)).isTrue()
    }

    @Test
    fun `Given the cookie banners setting off When a page loads a consent script Then only the main list applies`() {
        assertThat(engine.shouldBlock("https://cmp.example.net/consent.js", page, withCookieList = false)).isFalse()
        assertThat(engine.shouldBlock("https://ads.example.com/a.js", page, withCookieList = false)).isTrue()
    }

    @Test
    fun `Given a page When its cosmetic rules are read Then generic and site rules apply`() {
        val selection = engine.cosmeticSelectionFor("https://news.example.org/article")

        assertThat(selection.generic).containsExactly(".cookie-banner")
        assertThat(selection.specific).containsExactly(".cmp-wall")
    }

    @Test
    fun `Given a site where blocking is disabled When its cosmetic rules are read Then nothing is hidden and its requests pass`() {
        engine.updateDisabledSites(listOf("news.example.org"))

        assertThat(engine.cosmeticSelectionFor("https://news.example.org/article").isEmpty).isTrue()
        assertThat(engine.cosmeticSelectionFor("https://www.news.example.org/").isEmpty).isTrue()
        assertThat(engine.shouldBlock("https://cmp.example.net/consent.js", page)).isFalse()
        assertThat(engine.cosmeticSelectionFor("https://other.org/").generic).containsExactly(".cookie-banner")
    }

    @Test
    fun `Given a cookie list not downloaded yet When a page loads Then nothing is hidden`() {
        val empty = DefaultAdBlockEngine()

        assertThat(empty.cosmeticSelectionFor(page).isEmpty).isTrue()
        assertThat(empty.shouldBlock("https://cmp.example.net/consent.js", page)).isFalse()
    }

    @Test
    fun `Given a page that is not http When its cosmetic rules are read Then nothing is hidden`() {
        assertThat(engine.cosmeticSelectionFor("file:///sdcard/a.html").isEmpty).isTrue()
        assertThat(engine.cosmeticSelectionFor("about:blank").isEmpty).isTrue()
    }
}

package com.openctv.browser.features.adblock

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.features.adblock.data.CookieListParser
import com.openctv.browser.features.adblock.data.CosmeticRuleParser
import com.openctv.browser.features.adblock.domain.CosmeticFilters
import org.junit.Test

class CosmeticFiltersTest {

    private fun filters(list: String): CosmeticFilters = CosmeticRuleParser.parse(list.trimIndent().lineSequence())

    @Test
    fun `Given a generic rule When any page is opened Then its selector is hidden`() {
        val filters = filters("##.cookie-banner")

        assertThat(filters.selectionFor("news.example.org").generic).containsExactly(".cookie-banner")
        assertThat(filters.selectionFor("20minutes.fr").generic).containsExactly(".cookie-banner")
    }

    @Test
    fun `Given a domain rule When the site or one of its subdomains is opened Then the selector is hidden there only`() {
        val filters = filters("20minutes.fr##div.cmp-wall")

        assertThat(filters.selectionFor("20minutes.fr").specific).containsExactly("div.cmp-wall")
        assertThat(filters.selectionFor("www.20minutes.fr").specific).containsExactly("div.cmp-wall")
        assertThat(filters.selectionFor("not20minutes.fr").specific).isEmpty()
        assertThat(filters.selectionFor("20minutes.fr.evil.org").specific).isEmpty()
    }

    @Test
    fun `Given a rule for several domains When one of them is opened Then the selector applies`() {
        val filters = filters("a.com,b.org##.consent")

        assertThat(filters.selectionFor("b.org").specific).containsExactly(".consent")
        assertThat(filters.selectionFor("c.net").specific).isEmpty()
    }

    @Test
    fun `Given a generic rule and a domain exception When that site is opened Then the selector is kept visible there`() {
        val filters = filters(
            """
            ##.cookie-banner
            ##.consent
            shop.example.com#@#.cookie-banner
            """,
        )

        assertThat(filters.selectionFor("shop.example.com").generic).containsExactly(".consent")
        assertThat(filters.selectionFor("www.shop.example.com").generic).containsExactly(".consent")
        assertThat(filters.selectionFor("other.org").generic).containsExactly(".cookie-banner", ".consent")
    }

    @Test
    fun `Given a rule excluding a domain When that domain is opened Then the selector is not hidden there`() {
        val filters = filters("~a.com##.consent")

        assertThat(filters.selectionFor("b.com").generic).containsExactly(".consent")
        assertThat(filters.selectionFor("a.com").generic).isEmpty()
    }

    @Test
    fun `Given a global exception When any page is opened Then the selector is never hidden`() {
        val filters = filters(
            """
            ##.consent
            a.com##.consent
            #@#.consent
            """,
        )

        assertThat(filters.selectionFor("a.com").generic).isEmpty()
        assertThat(filters.selectionFor("a.com").specific).isEmpty()
    }

    @Test
    fun `Given a domain exception for a domain rule When that site is opened Then the domain selector is removed`() {
        val filters = filters(
            """
            a.com##.wall
            sub.a.com#@#.wall
            """,
        )

        assertThat(filters.selectionFor("a.com").specific).containsExactly(".wall")
        assertThat(filters.selectionFor("sub.a.com").specific).isEmpty()
    }

    @Test
    fun `Given a rule hiding a selector that breaks out of its rule When the list is compiled Then the rule is ignored`() {
        val filters = filters(
            """
            ##} body { background: url(https://evil.example/x) } .x
            a.com##.ok,} body {
            ##.fine
            """,
        )

        assertThat(filters.selectionFor("a.com").generic).containsExactly(".fine")
        assertThat(filters.selectionFor("a.com").specific).isEmpty()
    }

    @Test
    fun `Given comments extended syntax and wildcard domains When the list is compiled Then they are ignored without failing`() {
        val filters = filters(
            """
            ! Title: EasyList Cookie
            [Adblock Plus 2.0]
            a.com#?#div:-abp-has(.x)
            a.com#${'$'}#.x { display: none }
            a.com##+js(set, x, y)
            a.com##div:has-text(Cookies)
            google.*##.consent
            ##
            a.com##
            ##.valid
            """,
        )

        assertThat(filters.size).isEqualTo(1)
        assertThat(filters.selectionFor("a.com").generic).containsExactly(".valid")
    }

    @Test
    fun `Given a selector list in one rule When compiled Then each selector is kept separately`() {
        val filters = filters("##.a, #b:not(.c, .d)")

        assertThat(filters.selectionFor("x.org").generic).containsExactly(".a", "#b:not(.c, .d)")
    }

    @Test
    fun `Given a cookie list with network and cosmetic rules When parsed Then both are compiled in one pass`() {
        val list = CookieListParser.parse(
            """
            ||consent.example.com^${'$'}third-party
            ##.cookie-banner
            a.com##.wall
            """.trimIndent().lineSequence(),
        )

        assertThat(list.network.rulesFor("consent.example.com")).hasSize(1)
        assertThat(list.cosmetic.selectionFor("a.com").specific).containsExactly(".wall")
        assertThat(list.isEmpty).isFalse()
    }

    @Test
    fun `Given a page served by a host with many rules When the selection is read Then the generic list is shared and not copied`() {
        val filters = filters("##.a\n##.b")

        val first = filters.selectionFor("x.org").generic
        val second = filters.selectionFor("y.org").generic

        assertThat(first).isSameInstanceAs(second)
    }
}

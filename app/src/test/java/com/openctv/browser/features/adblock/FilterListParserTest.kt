package com.openctv.browser.features.adblock

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.features.adblock.data.FilterListParser
import org.junit.Test

class FilterListParserTest {

    @Test
    fun `Given an EasyList with comments and cosmetic rules When parsed Then only network rules are kept`() {
        val list = """
            [Adblock Plus 2.0]
            ! Title: EasyList
            example.com##.banner
            example.com#@#.banner
            ||ads.example.com^
            @@||ok.example.com^
        """.trimIndent()

        val filters = FilterListParser.parse(list)

        assertThat(filters.size).isEqualTo(2)
        assertThat(filters.rulesFor("ads.example.com")!!.single().isException).isFalse()
        assertThat(filters.rulesFor("ok.example.com")!!.single().isException).isTrue()
    }

    @Test
    fun `Given invalid or unsupported lines When parsed Then they are ignored without failing`() {
        val list = listOf(
            "||", "||^", "||bad domain^", "||a..b^", "||*.example.com^", "|http://x.com|",
            "/regex/", "||example.com/path/ad.js", "||example.com^\$script", "||example.com^\$domain=",
            "@@", "\$", "||ok.example.com^",
        ).joinToString("\n")

        val filters = FilterListParser.parse(list)

        assertThat(filters.size).isEqualTo(1)
        assertThat(filters.rulesFor("ok.example.com")).isNotNull()
    }

    @Test
    fun `Given options When parsed Then third-party and domain constraints are kept`() {
        val rule = FilterListParser.parseLine("||Ads.Example.com^\$third-party,domain=a.com|~b.a.com")!!

        assertThat(rule.domain).isEqualTo("ads.example.com")
        assertThat(rule.thirdPartyOnly).isTrue()
        assertThat(rule.includePageDomains).containsExactly("a.com")
        assertThat(rule.excludePageDomains).containsExactly("b.a.com")
    }

    @Test
    fun `Given an empty content When parsed Then the filters are empty`() {
        assertThat(FilterListParser.parse("").isEmpty).isTrue()
    }
}

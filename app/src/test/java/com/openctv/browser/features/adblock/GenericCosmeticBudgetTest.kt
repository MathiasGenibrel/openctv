package com.openctv.browser.features.adblock

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.webview.CosmeticStyleScript
import com.openctv.browser.features.adblock.data.CosmeticRuleParser
import com.openctv.browser.features.adblock.domain.CosmeticFilters
import com.openctv.browser.features.adblock.domain.GenericCosmeticBudget
import org.junit.Test

class GenericCosmeticBudgetTest {

    private fun filters(lines: List<String>): CosmeticFilters = CosmeticRuleParser.parse(lines.asSequence())

    /** ~15 000 règles génériques propres à un site, comme dans la vraie liste « EasyList Cookie ». */
    private val siteSpecificGeneric = (0 until 15_000).map { "###zpm-cookie-site$it" }

    private fun genericCharsOf(selectors: List<String>) = selectors.sumOf(CosmeticStyleScript::encodedLengthOf)

    @Test
    fun `Given a generic list the size of EasyList Cookie When compiled Then the generic rules injected in every page stay within the budget`() {
        val filters = filters(siteSpecificGeneric.map { "#$it" })

        val generic = filters.selectionFor("news.example.org").generic

        assertThat(generic).isNotEmpty()
        assertThat(genericCharsOf(generic)).isAtMost(GenericCosmeticBudget.MaxChars)
    }

    @Test
    fun `Given consent platform selectors at the end of a long list When compiled Then they are kept first`() {
        val vendors = listOf("###onetrust-consent-sdk", "###CybotCookiebotDialog", "##.didomi-notice-banner", "###qc-cmp2-container")
        val filters = filters(siteSpecificGeneric.map { "#$it" } + vendors)

        val generic = filters.selectionFor("news.example.org").generic

        assertThat(generic.take(vendors.size))
            .containsExactly("#onetrust-consent-sdk", "#CybotCookiebotDialog", ".didomi-notice-banner", "#qc-cmp2-container")
    }

    @Test
    fun `Given reusable banner names among site specific and complex selectors When over budget Then the reusable names come next`() {
        val complex = (0 until 3_000).map { "##div[class*=\"cookie-$it\"] > .x" }
        val reusable = listOf("###cookie-banner", "##.gdprNotice", "###cookieConsent_bar")
        val filters = filters(complex + siteSpecificGeneric.map { "#$it" } + reusable)

        val generic = filters.selectionFor("news.example.org").generic

        assertThat(generic.take(reusable.size)).containsExactly(".gdprNotice", "#cookie-banner", "#cookieConsent_bar")
        assertThat(generic.none { it.startsWith("div[") }).isTrue()
    }

    @Test
    fun `Given a generic list within the budget When compiled Then every generic rule is kept`() {
        val filters = filters(listOf("##.a", "##div > .b", "##[id^=\"c\"]"))

        assertThat(filters.selectionFor("x.org").generic).containsExactly(".a", "div > .b", "[id^=\"c\"]")
    }

    @Test
    fun `Given site rules and the full generic list When a page script is built Then it stays a few tens of kilobytes`() {
        val siteRules = (0 until 200).map { "news.example.org##.site-consent-$it" }
        val filters = filters(siteSpecificGeneric.map { "#$it" } + siteRules)

        val source = CosmeticStyleScript().build(filters.selectionFor("news.example.org"))!!

        assertThat(source.length).isAtMost(CosmeticStyleScript.MaxTotalChars + 1_000)
        assertThat(source).contains(".site-consent-199")
    }

    @Test
    fun `Given the generic budget When compared with the script cap Then room is left for the site rules`() {
        assertThat(GenericCosmeticBudget.MaxChars).isLessThan(CosmeticStyleScript.MaxTotalChars)
    }
}

package com.openctv.browser.core.webview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CosmeticSelectorTest {

    @Test
    fun `Given the usual cookie banner selectors When validated Then they are accepted`() {
        val selectors = listOf(
            "#didomi-host", ".cookie-banner", "div#onetrust-consent-sdk", "body > .cc-window", "a + div.consent",
            "[id^=\"sp_message_container_\"]", "[class*='cookie' i]", "div[data-nosnippet] > .modal",
            "#a:not(.b, .c)", "li:nth-child(2n+1) .x", "main:has(> .cookie-wall)", "*.cmp",
        )

        selectors.forEach { assertThat(CosmeticSelector.isSafe(it)).isTrue() }
    }

    @Test
    fun `Given a selector that could break out of its rule When validated Then it is refused`() {
        val dangerous = listOf(
            "} body { background: url(https://evil.example/x) }",
            ".a{color:red}", ".a;.b", "@import 'x'", ".a\\.b", "<script>", ".a/*", "a { }",
            ".x[href=\"javascript:alert(1)\"]", ".a:url(x)", "div:expression(alert(1))", ".a !important",
            "a[b=\"c\"]{", "é", "a\nb",
            ".a/* x */", "/**/.a", ".a\\7d body", "#a\\000000", "[a=\"}\"]", "[a=\"x\\\"]", "@media all", ".a\tb",
            "#a\u2028", ".a\u00a0", "#ｃookie", ".a</style><script>", "[style*=\"url(x)\"]", "[a=\"x\"];color:red",
        )

        dangerous.forEach { assertThat(CosmeticSelector.isSafe(it)).isFalse() }
    }

    @Test
    fun `Given procedural or exotic syntax When validated Then it is refused`() {
        val unsupported = listOf(
            "div:has-text(Cookies)", "div:-abp-contains(x)", "div:xpath(//a)", "+js(set, a, b)", "div::before",
            "div:matches-css(display: block)", "a:hover", "div:style(x)", ":not(", ".a)", "[a=\"b", "a[b]]", "a:not(:is(:not(:is(.x))))",
        )

        unsupported.forEach { assertThat(CosmeticSelector.isSafe(it)).isFalse() }
    }

    @Test
    fun `Given selectors that would hide the whole page When validated Then they are refused`() {
        listOf("*", "html", "BODY", "head", ":root").forEach { assertThat(CosmeticSelector.isSafe(it)).isFalse() }
    }

    @Test
    fun `Given an overlong selector When validated Then it is refused`() {
        assertThat(CosmeticSelector.isSafe("." + "a".repeat(CosmeticSelector.MaxLength - 1))).isTrue()
        assertThat(CosmeticSelector.isSafe("." + "a".repeat(CosmeticSelector.MaxLength))).isFalse()
        assertThat(CosmeticSelector.isSafe("")).isFalse()
        assertThat(CosmeticSelector.isSafe(" .a")).isFalse()
    }

    @Test
    fun `Given a selector list When split Then only top-level commas separate selectors`() {
        val parts = CosmeticSelector.splitList("#a, .b:not(.c, .d) ,[x=\"1,2\"]")

        assertThat(parts).containsExactly("#a", ".b:not(.c, .d)", "[x=\"1,2\"]").inOrder()
    }
}

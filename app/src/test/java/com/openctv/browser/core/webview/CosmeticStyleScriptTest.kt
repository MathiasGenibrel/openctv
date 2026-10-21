package com.openctv.browser.core.webview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CosmeticStyleScriptTest {

    private val script = CosmeticStyleScript()

    private fun build(generic: List<String> = emptyList(), specific: List<String> = emptyList()) =
        script.build(CosmeticSelection(generic, specific))

    /** Le script avec les sélecteurs retirés : ce qui reste doit toujours être le même modèle constant. */
    private fun skeletonOf(source: String, selectorsJson: String) = source.replace(selectorsJson, "__SELECTORS__")

    @Test
    fun `Given nothing to hide When building the script Then no script is produced`() {
        assertThat(build()).isNull()
    }

    @Test
    fun `Given only refused selectors When building the script Then no script is produced`() {
        assertThat(build(generic = listOf("} body {", "a{b}"), specific = listOf("@import"))).isNull()
    }

    @Test
    fun `Given different selectors When building scripts Then the code around them never changes`() {
        val first = build(generic = listOf("#didomi-host"))!!
        val second = build(generic = listOf(".cookie[x=\"1\"]"), specific = listOf("#onetrust"))!!

        assertThat(skeletonOf(first, "[\"#didomi-host\"]")).isEqualTo(
            skeletonOf(second, "[\"#onetrust\",\".cookie[x=\\\"1\\\"]\"]"),
        )
    }

    @Test
    fun `Given a selector with quotes When building the script Then it stays a JSON string literal`() {
        val source = build(generic = listOf("[class*='a'][id=\"b\"]"))!!

        assertThat(source).contains("[\"[class*='a'][id=\\\"b\\\"]\"]")
    }

    @Test
    fun `Given a refused selector among valid ones When building the script Then only the valid ones are injected`() {
        val source = build(generic = listOf("#ok", "} body { x: url(y) }", ".fine"))!!

        assertThat(source).contains("[\"#ok\",\".fine\"]")
        assertThat(source).doesNotContain("body")
        assertThat(source).doesNotContain("url(")
    }

    @Test
    fun `Given site rules and generic rules When building the script Then the site rules come first`() {
        val source = build(generic = listOf(".generic"), specific = listOf(".site"))!!

        assertThat(source.indexOf(".site")).isLessThan(source.indexOf(".generic"))
    }

    @Test
    fun `Given the script When read Then it only adds a style element and ignores every failure`() {
        val source = build(generic = listOf("#a"))!!

        assertThat(source).contains("document.createElement('style')")
        assertThat(source).contains("display:none!important")
        assertThat(source).contains("catch(z){}")
        assertThat(source).doesNotContain("innerHTML")
        assertThat(source).doesNotContain("return ")
        assertThat(source).doesNotContain("fetch(")
        assertThat(source).doesNotContain("XMLHttpRequest")
    }

    @Test
    fun `Given far more selectors than the budget When building the script Then the injected stylesheet stays bounded`() {
        val many = (0 until 20_000).map { ".cookie-banner-number-$it" }

        val source = build(generic = many)!!

        assertThat(source.length).isLessThan(CosmeticStyleScript.MaxTotalChars * 2)
        assertThat(source).contains(".cookie-banner-number-0\"")
        assertThat(source).doesNotContain(".cookie-banner-number-19999\"")
    }

    @Test
    fun `Given the same generic list for two pages When building the scripts Then both pages get the same rules`() {
        val generic = listOf("#a", ".b")

        val first = build(generic = generic, specific = listOf(".x"))!!
        val second = build(generic = generic, specific = listOf(".y"))!!

        assertThat(first).contains("[\".x\",\"#a\",\".b\"]")
        assertThat(second).contains("[\".y\",\"#a\",\".b\"]")
    }
}

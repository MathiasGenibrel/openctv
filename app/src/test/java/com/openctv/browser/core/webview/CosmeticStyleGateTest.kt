package com.openctv.browser.core.webview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CosmeticStyleGateTest {

    /** Chaque hôte masque un sélecteur à son nom : le script dit pour quel hôte il a été préparé. */
    private val requestedHosts = mutableListOf<String>()
    private val gate = CosmeticStyleGate(
        CosmeticSelectionSource { pageUrl ->
            val host = pageUrl.substringAfter("://").substringBefore('/')
            requestedHosts += host
            if (host == "clean.example.org") CosmeticSelection.EMPTY else CosmeticSelection(emptyList(), listOf("#$host"))
        },
    )

    private fun open(url: String) {
        gate.onMainFrameRequest(url)
        gate.onPageStarted(url)
    }

    @Test
    fun `Given a page shown by the guard When it is first rendered Then its style is applied once`() {
        open("https://news.example.org/a")

        val first = gate.scriptToApply("https://news.example.org/a")
        val second = gate.scriptToApply("https://news.example.org/a")

        assertThat(first).contains("#news.example.org")
        assertThat(second).isNull()
    }

    @Test
    fun `Given a page requested off the main thread When it is rendered Then the prepared script is reused`() {
        open("https://news.example.org/a")

        gate.scriptToApply("https://news.example.org/a")

        assertThat(requestedHosts).containsExactly("news.example.org")
    }

    @Test
    fun `Given a redirect to another host When the page is rendered Then the style is the one of the shown host`() {
        gate.onMainFrameRequest("https://short.example/x")
        gate.onPageStarted("https://news.example.org/a")

        val script = gate.scriptToApply("https://news.example.org/a")

        assertThat(script).contains("#news.example.org")
        assertThat(script).doesNotContain("#short.example")
    }

    @Test
    fun `Given a main frame error reported after the page started When it is rendered Then no style is applied`() {
        open("https://down.example.org/")
        gate.onMainFrameBlocked("https://down.example.org/")

        assertThat(gate.scriptToApply("https://down.example.org/")).isNull()
    }

    @Test
    fun `Given a certificate warning reported before the error page starts When it is rendered Then no style is applied`() {
        gate.onMainFrameRequest("https://bad-cert.example.org/")
        gate.onMainFrameBlocked("https://bad-cert.example.org/")
        gate.onPageStarted("https://bad-cert.example.org/")

        assertThat(gate.scriptToApply("https://bad-cert.example.org/")).isNull()
    }

    @Test
    fun `Given a blocked page When the user retries it Then the new load gets its style`() {
        gate.onMainFrameRequest("https://down.example.org/")
        gate.onMainFrameBlocked("https://down.example.org/")

        open("https://down.example.org/")

        assertThat(gate.scriptToApply("https://down.example.org/")).contains("#down.example.org")
    }

    @Test
    fun `Given a page that is not http When it is rendered Then no style is applied`() {
        open("about:blank")

        assertThat(gate.scriptToApply("about:blank")).isNull()
    }

    @Test
    fun `Given a page with nothing to hide When it is rendered Then no script is evaluated`() {
        open("https://clean.example.org/")

        assertThat(gate.scriptToApply("https://clean.example.org/")).isNull()
    }

    @Test
    fun `Given a styled page When the next page starts Then that page gets its own style`() {
        open("https://news.example.org/a")
        gate.scriptToApply("https://news.example.org/a")

        open("https://other.example.net/")

        assertThat(gate.scriptToApply("https://other.example.net/")).contains("#other.example.net")
    }
}

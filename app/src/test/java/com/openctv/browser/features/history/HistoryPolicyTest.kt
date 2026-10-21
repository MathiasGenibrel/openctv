package com.openctv.browser.features.history

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.features.history.domain.HistoryPolicy
import java.time.Instant
import org.junit.Test

class HistoryPolicyTest {

    @Test
    fun `Given web pages When deciding what to record Then http and https pages are kept`() {
        assertThat(HistoryPolicy.isRecordable("https://www.arte.tv/fr/")).isTrue()
        assertThat(HistoryPolicy.isRecordable("http://nas.maison:8080/")).isTrue()
    }

    @Test
    fun `Given addresses that are not web pages When deciding what to record Then they are ignored`() {
        listOf("about:blank", "data:text/html,error", "file:///sdcard/a.html", "intent://x", "https://", "", "  ")
            .forEach { assertThat(HistoryPolicy.isRecordable(it)).isFalse() }
    }

    @Test
    fun `Given the latest entry is the same address When the page is visited again Then the visits merge`() {
        assertThat(HistoryPolicy.mergesWithLatest("https://arte.tv/", "https://arte.tv/")).isTrue()
        assertThat(HistoryPolicy.mergesWithLatest("https://arte.tv/", "https://arte.tv/fr/")).isFalse()
        assertThat(HistoryPolicy.mergesWithLatest(null, "https://arte.tv/")).isFalse()
    }

    @Test
    fun `Given a merged visit without title When completing the entry Then the known title is kept`() {
        assertThat(HistoryPolicy.titleOf(previous = "ARTE", incoming = "")).isEqualTo("ARTE")
        assertThat(HistoryPolicy.titleOf(previous = "ARTE", incoming = " ARTE - Replay ")).isEqualTo("ARTE - Replay")
    }

    @Test
    fun `Given now When computing the age limit Then entries older than 90 days are out`() {
        val now = Instant.parse("2026-10-21T12:00:00Z")

        assertThat(HistoryPolicy.cutoff(now)).isEqualTo(Instant.parse("2026-07-23T12:00:00Z"))
    }
}

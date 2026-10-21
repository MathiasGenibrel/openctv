package com.openctv.browser.features.history

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.features.history.domain.DayLabel
import com.openctv.browser.features.history.domain.HistoryEntry
import com.openctv.browser.features.history.domain.groupByDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class HistoryGroupingTest {

    private val paris = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 10, 21)

    private fun entry(id: Long, at: String) = HistoryEntry(id, "https://site$id.example", "Site $id", Instant.parse(at))

    @Test
    fun `Given visits today yesterday and earlier When grouping Then days are labelled Today Yesterday then the date`() {
        val entries = listOf(
            entry(1, "2026-10-21T16:00:00Z"),
            entry(2, "2026-10-21T08:00:00Z"),
            entry(3, "2026-10-20T20:00:00Z"),
            entry(4, "2026-10-12T20:00:00Z"),
        )

        val days = groupByDay(entries, today, paris)

        assertThat(days.map { it.label }).containsExactly(
            DayLabel.Today, DayLabel.Yesterday, DayLabel.On(LocalDate.of(2026, 10, 12)),
        ).inOrder()
        assertThat(days[0].entries.map { it.id }).containsExactly(1L, 2L).inOrder()
    }

    @Test
    fun `Given a visit just before midnight UTC When grouping in Paris Then it belongs to the next local day`() {
        // 22:30 UTC le 20 = 00:30 à Paris (UTC+2) le 21 : « Aujourd'hui », pas « Hier ».
        val days = groupByDay(listOf(entry(1, "2026-10-20T22:30:00Z")), today, paris)

        assertThat(days.single().label).isEqualTo(DayLabel.Today)
    }

    @Test
    fun `Given no visit When grouping Then there is no day`() {
        assertThat(groupByDay(emptyList(), today, paris)).isEmpty()
    }

    @Test
    fun `Given a page without title When labelling its row Then the domain without www is shown`() {
        val untitled = HistoryEntry(1, "https://www.arte.tv/fr/", "", Instant.EPOCH)

        assertThat(untitled.label).isEqualTo("arte.tv")
        assertThat(untitled.domain).isEqualTo("arte.tv")
    }
}

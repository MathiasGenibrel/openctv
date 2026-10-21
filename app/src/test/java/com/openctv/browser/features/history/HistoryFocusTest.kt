package com.openctv.browser.features.history

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.features.history.domain.HistoryEntry
import com.openctv.browser.features.history.domain.HistoryFocus
import java.time.Instant
import org.junit.Test

class HistoryFocusTest {

    private fun entries(vararg ids: Long) = ids.map { HistoryEntry(it, "https://s$it.example", "S$it", Instant.EPOCH) }

    @Test
    fun `Given a middle entry removed When choosing the focus Then the entry that takes its place is focused`() {
        assertThat(HistoryFocus.afterRemoving(entries(1, 2, 3), removedId = 2)).isEqualTo(HistoryFocus.Entry(3))
    }

    @Test
    fun `Given the last entry removed When choosing the focus Then the previous entry is focused`() {
        assertThat(HistoryFocus.afterRemoving(entries(1, 2, 3), removedId = 3)).isEqualTo(HistoryFocus.Entry(2))
    }

    @Test
    fun `Given the only entry removed When choosing the focus Then the back button is focused`() {
        assertThat(HistoryFocus.afterRemoving(entries(1), removedId = 1)).isEqualTo(HistoryFocus.BackButton)
    }

    @Test
    fun `Given the screen opens When choosing the initial focus Then it is the first entry or the back button when empty`() {
        assertThat(HistoryFocus.initial(entries(5, 4))).isEqualTo(HistoryFocus.Entry(5))
        assertThat(HistoryFocus.initial(emptyList())).isEqualTo(HistoryFocus.BackButton)
    }

    @Test
    fun `Given the user comes back from a page opened from the history When choosing the initial focus Then its entry is focused again`() {
        assertThat(HistoryFocus.initial(entries(9, 5, 4), rememberedId = 5)).isEqualTo(HistoryFocus.Entry(5))
    }

    @Test
    fun `Given the remembered entry is gone When choosing the initial focus Then the first entry is focused`() {
        assertThat(HistoryFocus.initial(entries(9, 4), rememberedId = 5)).isEqualTo(HistoryFocus.Entry(9))
        assertThat(HistoryFocus.initial(emptyList(), rememberedId = 5)).isEqualTo(HistoryFocus.BackButton)
    }
}

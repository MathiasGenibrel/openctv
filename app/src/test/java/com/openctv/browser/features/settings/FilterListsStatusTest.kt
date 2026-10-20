package com.openctv.browser.features.settings

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.features.settings.ui.FilterListsStatus
import com.openctv.browser.features.settings.ui.FilterListsSummary
import com.openctv.browser.features.settings.ui.summary
import java.time.Duration
import java.time.Instant
import org.junit.Test

class FilterListsStatusTest {

    private val now = Instant.parse("2026-10-20T12:00:00Z")

    @Test
    fun `Given lists never downloaded When the row is shown Then it says never updated`() {
        assertThat(FilterListsStatus().summary(now)).isEqualTo(FilterListsSummary.Never)
    }

    @Test
    fun `Given lists updated two days ago When the row is shown Then it says two days ago`() {
        val status = FilterListsStatus(lastUpdated = now.minus(Duration.ofDays(2)).minusSeconds(60))
        assertThat(status.summary(now)).isEqualTo(FilterListsSummary.DaysAgo(2))
    }

    @Test
    fun `Given lists updated this morning When the row is shown Then it says today`() {
        val status = FilterListsStatus(lastUpdated = now.minus(Duration.ofHours(3)))
        assertThat(status.summary(now)).isEqualTo(FilterListsSummary.Today)
    }

    @Test
    fun `Given an update in progress or failed When the row is shown Then progress and failure win over the date`() {
        val old = now.minus(Duration.ofDays(9))
        assertThat(FilterListsStatus(old, isUpdating = true, hasFailed = true).summary(now)).isEqualTo(FilterListsSummary.Updating)
        assertThat(FilterListsStatus(old, hasFailed = true).summary(now)).isEqualTo(FilterListsSummary.Failed)
    }
}

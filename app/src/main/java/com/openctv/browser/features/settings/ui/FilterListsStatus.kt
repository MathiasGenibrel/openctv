package com.openctv.browser.features.settings.ui

import java.time.Duration
import java.time.Instant

/** État des listes de filtres tel que les Réglages l'affichent (fourni par la racine de composition). */
data class FilterListsStatus(
    val lastUpdated: Instant? = null,
    val isUpdating: Boolean = false,
    val hasFailed: Boolean = false,
)

/** Résumé affichable de la ligne « Listes de filtres ». */
sealed interface FilterListsSummary {
    data object Updating : FilterListsSummary
    data object Failed : FilterListsSummary
    data object Never : FilterListsSummary
    data object Today : FilterListsSummary
    data class DaysAgo(val days: Int) : FilterListsSummary
}

fun FilterListsStatus.summary(now: Instant): FilterListsSummary {
    val updatedAt = lastUpdated
    return when {
        isUpdating -> FilterListsSummary.Updating
        hasFailed -> FilterListsSummary.Failed
        updatedAt == null -> FilterListsSummary.Never
        else -> {
            val days = Duration.between(updatedAt, now).toDays().toInt()
            if (days <= 0) FilterListsSummary.Today else FilterListsSummary.DaysAgo(days)
        }
    }
}

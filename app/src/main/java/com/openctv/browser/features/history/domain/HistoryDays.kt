package com.openctv.browser.features.history.domain

import java.time.LocalDate
import java.time.ZoneId

/** Intitulé d'un jour de l'historique ; le texte est composé par l'interface (langue, format de date). */
sealed interface DayLabel {
    data object Today : DayLabel
    data object Yesterday : DayLabel
    data class On(val date: LocalDate) : DayLabel
}

/** Entrées d'un même jour, de la plus récente à la plus ancienne. */
data class HistoryDay(val label: DayLabel, val entries: List<HistoryEntry>)

/**
 * Regroupe [entries] (de la plus récente à la plus ancienne) par jour calendaire de [zone] ; [today] est le jour
 * courant dans cette même zone. Le fuseau est injecté : le regroupement ne dépend pas de celui de la machine.
 */
fun groupByDay(entries: List<HistoryEntry>, today: LocalDate, zone: ZoneId): List<HistoryDay> =
    entries
        .groupBy { it.visitedAt.atZone(zone).toLocalDate() }
        .map { (date, dayEntries) -> HistoryDay(dayLabelOf(date, today), dayEntries) }

private fun dayLabelOf(date: LocalDate, today: LocalDate): DayLabel = when (date) {
    today -> DayLabel.Today
    today.minusDays(1) -> DayLabel.Yesterday
    else -> DayLabel.On(date)
}

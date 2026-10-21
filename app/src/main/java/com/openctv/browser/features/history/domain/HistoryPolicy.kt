package com.openctv.browser.features.history.domain

import java.time.Duration
import java.time.Instant

/** Règles de l'historique, en Kotlin pur : quoi enregistrer, quand fusionner, jusqu'où garder. */
object HistoryPolicy {
    const val MAX_ENTRIES = 1_000
    val MAX_AGE: Duration = Duration.ofDays(90)

    /**
     * Seules les pages web ([url] en `http`/`https`) sont enregistrées. Les écrans d'avertissement et d'erreur n'ont
     * jamais d'adresse propre : le navigateur ne les enregistre pas, et ce filtre écarte `about:`, `data:`, `file:`…
     */
    fun isRecordable(url: String): Boolean {
        val candidate = url.trim()
        val isWebPage = candidate.startsWith("http://", ignoreCase = true) || candidate.startsWith("https://", ignoreCase = true)
        return isWebPage && candidate.length > candidate.indexOf("://") + 3
    }

    /** Une visite de la même adresse que la dernière entrée la complète au lieu d'ajouter une ligne (rechargement, titre tardif). */
    fun mergesWithLatest(latestUrl: String?, url: String): Boolean = latestUrl != null && latestUrl.trim() == url.trim()

    /** Les entrées visitées avant cet instant sont supprimées. */
    fun cutoff(now: Instant): Instant = now.minus(MAX_AGE)

    /** Titre conservé quand une visite fusionnée n'en apporte pas : on ne perd jamais un titre déjà connu. */
    fun titleOf(previous: String?, incoming: String): String = incoming.trim().ifBlank { previous.orEmpty() }
}

package com.openctv.browser.features.history.domain

import com.openctv.browser.core.security.SiteHost
import java.time.Instant

/** Une page visitée sur la TV. [title] est vide quand la page n'en a pas (ou pas encore) donné. */
data class HistoryEntry(
    val id: Long,
    val url: String,
    val title: String,
    val visitedAt: Instant,
) {
    /** Domaine affiché (`arte.tv`) : l'hôte sans `www.` ; l'adresse entière si elle n'a pas d'hôte lisible. */
    val domain: String get() = SiteHost.of(url)?.removePrefix("www.") ?: url

    /** Libellé de la ligne : le titre de la page, à défaut son domaine. */
    val label: String get() = title.ifBlank { domain }
}

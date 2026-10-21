package com.openctv.browser.features.adblock.domain

/**
 * Règle de masquage `[domaines]##sélecteurs` (ou exception `#@#`), réduite à des sélecteurs déjà validés
 * par `CosmeticSelector`. [includeDomains] vide : règle générique (tous les sites).
 */
data class CosmeticRule(
    val selectors: List<String>,
    val isException: Boolean,
    val includeDomains: Set<String> = emptySet(),
    val excludeDomains: Set<String> = emptySet(),
)

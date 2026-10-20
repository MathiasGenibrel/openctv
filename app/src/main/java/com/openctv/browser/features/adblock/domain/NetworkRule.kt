package com.openctv.browser.features.adblock.domain

/**
 * Règle réseau EasyList réduite à ce que le moteur sait décider en O(1) : un domaine
 * (et ses sous-domaines), éventuellement restreint par des options.
 */
data class NetworkRule(
    val domain: String,
    val isException: Boolean,
    val thirdPartyOnly: Boolean = false,
    val firstPartyOnly: Boolean = false,
    val includePageDomains: Set<String> = emptySet(),
    val excludePageDomains: Set<String> = emptySet(),
)

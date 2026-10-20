package com.openctv.browser.features.adblock.domain

import java.util.Locale

class DefaultAdBlockEngine : AdBlockEngine {

    @Volatile
    private var filters: CompiledFilters = CompiledFilters.EMPTY

    /** Instantané immuable : écritures rares (restauration, action utilisateur), lectures sans verrou. */
    @Volatile
    private var disabledSites: Set<String> = emptySet()

    override fun updateFilters(filters: CompiledFilters) {
        this.filters = filters
    }

    override fun updateDisabledSites(sites: Iterable<String>) {
        disabledSites = sites.mapNotNullTo(HashSet()) { normalize(it) }
    }

    override fun isEnabledForSite(siteHost: String): Boolean {
        val host = normalize(siteHost) ?: return true
        return !HostUtils.isCoveredBy(host, disabledSites)
    }

    override fun shouldBlock(requestUrl: String, pageUrl: String?): Boolean {
        val current = filters
        if (current.isEmpty) return false

        val requestHost = HostUtils.hostOf(requestUrl) ?: return false
        val pageHost = pageUrl?.let(HostUtils::hostOf)
        val isPageDisabled = pageHost != null && HostUtils.isCoveredBy(pageHost, disabledSites)
        if (isPageDisabled) return false

        var blocked = false
        var excepted = false
        HostUtils.forEachSuffix(requestHost) { suffix ->
            current.rulesFor(suffix)?.forEach { rule ->
                if (rule.appliesTo(requestHost, pageHost)) {
                    if (rule.isException) excepted = true else blocked = true
                }
            }
            !excepted
        }
        return blocked && !excepted
    }

    private fun NetworkRule.appliesTo(requestHost: String, pageHost: String?): Boolean {
        val needsPage = thirdPartyOnly || firstPartyOnly ||
            includePageDomains.isNotEmpty() || excludePageDomains.isNotEmpty()
        if (!needsPage) return true
        // Sans page connue (ex. navigation principale), une option contextuelle ne s'applique pas.
        if (pageHost == null) return false

        val isThirdParty = HostUtils.isThirdParty(requestHost, pageHost)
        if (thirdPartyOnly && !isThirdParty) return false
        if (firstPartyOnly && isThirdParty) return false
        val isOutsideIncludedPages = includePageDomains.isNotEmpty() &&
            !HostUtils.isCoveredBy(pageHost, includePageDomains)
        if (isOutsideIncludedPages) return false
        if (HostUtils.isCoveredBy(pageHost, excludePageDomains)) return false
        return true
    }

    private fun normalize(site: String): String? =
        (if ("://" in site) HostUtils.hostOf(site) else site.trim().trimEnd('.').lowercase(Locale.ROOT))
            ?.takeIf { it.isNotEmpty() }
}

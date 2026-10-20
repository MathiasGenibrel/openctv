package com.openctv.browser.features.adblock.domain

import java.util.Locale

class DefaultAdBlockEngine : AdBlockEngine {

    @Volatile
    private var filters: CompiledFilters = CompiledFilters.EMPTY

    /** Copy-on-write : écritures rares (action utilisateur), lectures sans verrou. */
    @Volatile
    private var disabledSites: Set<String> = emptySet()

    override fun updateFilters(filters: CompiledFilters) {
        this.filters = filters
    }

    @Synchronized
    override fun setEnabledForSite(siteHost: String, enabled: Boolean) {
        val host = normalize(siteHost) ?: return
        disabledSites = if (enabled) disabledSites - host else disabledSites + host
    }

    override fun isEnabledForSite(siteHost: String): Boolean {
        val host = normalize(siteHost) ?: return true
        return !isSiteDisabled(host)
    }

    override fun shouldBlock(requestUrl: String, pageUrl: String?): Boolean {
        val current = filters
        if (current.isEmpty) return false

        val requestHost = HostUtils.hostOf(requestUrl) ?: return false
        val pageHost = pageUrl?.let(HostUtils::hostOf)
        val isPageDisabled = pageHost != null && isSiteDisabled(pageHost)
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

    private fun isSiteDisabled(pageHost: String): Boolean {
        val disabled = disabledSites
        if (disabled.isEmpty()) return false
        var result = false
        HostUtils.forEachSuffix(pageHost) { suffix ->
            result = suffix in disabled
            !result
        }
        return result
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
        if (includePageDomains.isNotEmpty() && !matchesAny(pageHost, includePageDomains)) return false
        if (matchesAny(pageHost, excludePageDomains)) return false
        return true
    }

    private fun matchesAny(host: String, domains: Set<String>): Boolean {
        var found = false
        HostUtils.forEachSuffix(host) { suffix ->
            found = suffix in domains
            !found
        }
        return found
    }

    private fun normalize(site: String): String? =
        (if ("://" in site) HostUtils.hostOf(site) else site.trim().trimEnd('.').lowercase(Locale.ROOT))
            ?.takeIf { it.isNotEmpty() }
}

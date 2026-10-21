package com.openctv.browser.features.adblock.domain

import com.openctv.browser.core.webview.CosmeticSelection

class DefaultAdBlockEngine : AdBlockEngine {

    @Volatile
    private var filters: CompiledFilters = CompiledFilters.EMPTY

    /** Instantané immuable : écritures rares (restauration, action utilisateur), lectures sans verrou. */
    @Volatile
    private var disabledSites: Set<String> = emptySet()

    /** Liste des bandeaux de cookies : un seul instantané, réseau et cosmétique remplacés ensemble. */
    private class CookieFilters(val network: CompiledFilters, val cosmetic: CosmeticFilters)

    @Volatile
    private var cookieFilters = CookieFilters(CompiledFilters.EMPTY, CosmeticFilters.EMPTY)

    override fun updateFilters(filters: CompiledFilters) {
        this.filters = filters
    }

    override fun updateCookieFilters(network: CompiledFilters, cosmetic: CosmeticFilters) {
        cookieFilters = CookieFilters(network, cosmetic)
    }

    override fun cosmeticSelectionFor(pageUrl: String): CosmeticSelection {
        val cosmetic = cookieFilters.cosmetic
        if (cosmetic.isEmpty) return CosmeticSelection.EMPTY
        val pageHost = HostUtils.hostOf(pageUrl) ?: return CosmeticSelection.EMPTY
        if (HostUtils.isCoveredBy(pageHost, disabledSites)) return CosmeticSelection.EMPTY
        return cosmetic.selectionFor(pageHost)
    }

    override fun updateDisabledSites(sites: Iterable<String>) {
        disabledSites = sites.mapNotNullTo(HashSet()) { normalize(it) }
    }

    override fun isEnabledForSite(siteHost: String): Boolean {
        val host = normalize(siteHost) ?: return true
        return !HostUtils.isCoveredBy(host, disabledSites)
    }

    override fun shouldBlock(requestUrl: String, pageUrl: String?, withCookieList: Boolean): Boolean {
        val main = filters
        val cookie = if (withCookieList) cookieFilters.network else CompiledFilters.EMPTY
        if (main.isEmpty && cookie.isEmpty) return false

        val requestHost = HostUtils.hostOf(requestUrl) ?: return false
        val pageHost = pageUrl?.let(HostUtils::hostOf)
        val isPageDisabled = pageHost != null && HostUtils.isCoveredBy(pageHost, disabledSites)
        if (isPageDisabled) return false

        return isBlockedBy(main, requestHost, pageHost) || isBlockedBy(cookie, requestHost, pageHost)
    }

    /** Chaque liste décide seule : l'exception d'une liste ne lève pas le blocage d'une autre. */
    private fun isBlockedBy(list: CompiledFilters, requestHost: String, pageHost: String?): Boolean {
        if (list.isEmpty) return false
        var blocked = false
        var excepted = false
        HostUtils.forEachSuffix(requestHost) { suffix ->
            list.rulesFor(suffix)?.forEach { rule ->
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

    private fun normalize(site: String): String? = HostUtils.siteKeyOf(site)
}

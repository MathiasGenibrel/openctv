package com.openctv.browser.features.adblock.data

import com.openctv.browser.features.adblock.domain.CompiledFilters
import com.openctv.browser.features.adblock.domain.CosmeticFilters
import com.openctv.browser.features.adblock.domain.CosmeticRule
import com.openctv.browser.features.adblock.domain.NetworkRule

/** Liste « EasyList Cookie » compilée : règles réseau (`||domaine^`) et règles de masquage (`##sélecteur`). */
class CookieList(val network: CompiledFilters, val cosmetic: CosmeticFilters) {
    val isEmpty: Boolean get() = network.isEmpty && cosmetic.isEmpty
}

object CookieListParser {
    /** Un seul passage sur [lines] (flux consommé une fois). */
    fun parse(lines: Sequence<String>): CookieList {
        val network = ArrayList<NetworkRule>()
        val cosmetic = ArrayList<CosmeticRule>()
        for (line in lines) {
            FilterListParser.parseLine(line)?.let(network::add)
            CosmeticRuleParser.parseLine(line)?.let(cosmetic::add)
        }
        return CookieList(CompiledFilters.of(network), CosmeticFilters.of(cosmetic))
    }
}

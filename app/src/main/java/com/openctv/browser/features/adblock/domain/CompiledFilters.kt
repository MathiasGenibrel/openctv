package com.openctv.browser.features.adblock.domain

/**
 * Structure immuable précompilée : domaine de règle -> règles. Construite hors du chemin de
 * navigation, partagée sans verrou entre threads.
 */
class CompiledFilters private constructor(private val rulesByDomain: Map<String, List<NetworkRule>>) {

    val isEmpty: Boolean get() = rulesByDomain.isEmpty()
    val size: Int get() = rulesByDomain.values.sumOf { it.size }

    fun rulesFor(domain: String): List<NetworkRule>? = rulesByDomain[domain]

    companion object {
        val EMPTY = CompiledFilters(emptyMap())

        fun of(rules: Iterable<NetworkRule>): CompiledFilters {
            val grouped = rules.groupBy { it.domain }
            return if (grouped.isEmpty()) EMPTY else CompiledFilters(HashMap(grouped))
        }
    }
}

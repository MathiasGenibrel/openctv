package com.openctv.browser.features.adblock.domain

import com.openctv.browser.core.webview.CosmeticSelection

/**
 * Règles cosmétiques compilées, indexées par domaine : une page ne coûte qu'un parcours des suffixes de son hôte
 * (a.b.c, b.c, c). Immuable, construite hors du chemin de navigation, partagée sans verrou entre threads.
 *
 * - génériques (`##sel`) : valent partout, sauf exception globale (`#@#sel`) ou exception sur le domaine de la page ;
 *   injectées dans chaque page, elles sont réduites une fois pour toutes aux plus utiles ([GenericCosmeticBudget]) ;
 * - par domaine (`site.com##sel`) : valent pour le domaine et ses sous-domaines ;
 * - exceptions par domaine (`site.com#@#sel`) et exclusions (`~site.com##sel`) retirent le sélecteur sur ce domaine.
 */
class CosmeticFilters private constructor(
    private val generic: List<String>,
    private val byDomain: Map<String, List<String>>,
    private val exceptionsByDomain: Map<String, Set<String>>,
) {
    val isEmpty: Boolean get() = generic.isEmpty() && byDomain.isEmpty()
    val size: Int get() = generic.size + byDomain.values.sumOf { it.size }

    /** Sélecteurs à masquer sur [host]. La liste générique est rendue telle quelle quand aucune exception ne la touche. */
    fun selectionFor(host: String): CosmeticSelection {
        if (isEmpty) return CosmeticSelection.EMPTY
        var specific: MutableList<String>? = null
        var excepted: MutableSet<String>? = null
        HostUtils.forEachSuffix(host) { suffix ->
            byDomain[suffix]?.let { (specific ?: ArrayList<String>().also { specific = it }).addAll(it) }
            exceptionsByDomain[suffix]?.let { (excepted ?: HashSet<String>().also { excepted = it }).addAll(it) }
            true
        }
        val exceptions = excepted
        val genericForHost = if (exceptions == null) generic else generic.filter { it !in exceptions }
        val specificForHost = specific?.let { list ->
            val kept = if (exceptions == null) list else list.filter { it !in exceptions }
            kept.distinct()
        } ?: emptyList()
        return CosmeticSelection(genericForHost, specificForHost)
    }

    companion object {
        val EMPTY = CosmeticFilters(emptyList(), emptyMap(), emptyMap())

        fun of(rules: Iterable<CosmeticRule>): CosmeticFilters {
            val hidden = LinkedHashSet<String>()
            val byDomain = HashMap<String, MutableList<String>>()
            val globalExceptions = HashSet<String>()
            val exceptionsByDomain = HashMap<String, MutableSet<String>>()

            for (rule in rules) {
                val isGeneric = rule.includeDomains.isEmpty()
                when {
                    rule.isException && isGeneric -> globalExceptions += rule.selectors
                    rule.isException -> rule.includeDomains.forEach { domain ->
                        exceptionsByDomain.getOrPut(domain) { HashSet() } += rule.selectors
                    }
                    isGeneric -> hidden += rule.selectors
                    else -> rule.includeDomains.forEach { domain ->
                        byDomain.getOrPut(domain) { ArrayList() } += rule.selectors
                    }
                }
                if (!rule.isException) {
                    rule.excludeDomains.forEach { domain ->
                        exceptionsByDomain.getOrPut(domain) { HashSet() } += rule.selectors
                    }
                }
            }

            val generic = GenericCosmeticBudget.select(hidden.filter { it !in globalExceptions })
            if (generic.isEmpty() && byDomain.isEmpty()) return EMPTY
            // Un sélecteur d'exception globale retire aussi les règles par domaine du même sélecteur.
            val specific = byDomain.mapValues { (_, selectors) -> selectors.filter { it !in globalExceptions }.distinct() }
            return CosmeticFilters(generic, HashMap(specific), HashMap(exceptionsByDomain))
        }
    }
}

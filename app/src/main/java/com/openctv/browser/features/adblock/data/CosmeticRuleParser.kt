package com.openctv.browser.features.adblock.data

import com.openctv.browser.core.webview.CosmeticSelector
import com.openctv.browser.features.adblock.domain.CosmeticFilters
import com.openctv.browser.features.adblock.domain.CosmeticRule
import java.util.Locale

/**
 * Parse les règles de masquage d'une liste EasyList : `##sélecteur` (générique), `domaine1,domaine2##sélecteur`,
 * `~domaine` (exclusion) et les exceptions `#@#`.
 *
 * Une liste téléchargée n'est pas fiable : chaque sélecteur doit passer la liste blanche de [CosmeticSelector]. Une règle
 * dont **un seul** sélecteur est refusé est ignorée en entier (ex. `} body { background: url(x) }`).
 *
 * Ignorés sans erreur : règles étendues (`#?#`, `#$#`, `#@?#`, `#@$#`, `+js(…)`, `:has-text(…)`, `:-abp-…`),
 * domaines avec joker (`google.*`), domaines invalides. Toute ligne invalide est ignorée.
 */
object CosmeticRuleParser {

    private const val HideMarker = "##"
    private const val ExceptionMarker = "#@#"

    fun parse(lines: Sequence<String>): CosmeticFilters = CosmeticFilters.of(lines.mapNotNull(::parseLine).asIterable())

    fun parseLine(raw: String): CosmeticRule? = try {
        parseRule(raw.trim())
    } catch (_: RuntimeException) {
        null
    }

    private fun parseRule(line: String): CosmeticRule? {
        if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return null
        val hideIndex = line.indexOf(HideMarker)
        val exceptionIndex = line.indexOf(ExceptionMarker)
        val isException = exceptionIndex >= 0 && (hideIndex < 0 || exceptionIndex < hideIndex)
        val markerIndex = if (isException) exceptionIndex else hideIndex
        if (markerIndex < 0) return null
        val marker = if (isException) ExceptionMarker else HideMarker

        val domains = parseDomains(line.substring(0, markerIndex)) ?: return null
        val selectors = CosmeticSelector.splitList(line.substring(markerIndex + marker.length))
        val areAllSafe = selectors.isNotEmpty() && selectors.all(CosmeticSelector::isSafe)
        if (!areAllSafe) return null
        return CosmeticRule(selectors, isException, domains.first, domains.second)
    }

    /** (inclus, exclus) ; `null` si un domaine est invalide ou utilise un joker. */
    private fun parseDomains(text: String): Pair<Set<String>, Set<String>>? {
        if (text.isEmpty()) return emptySet<String>() to emptySet()
        val include = LinkedHashSet<String>()
        val exclude = LinkedHashSet<String>()
        for (entry in text.split(',')) {
            val isExclusion = entry.startsWith("~")
            val domain = entry.removePrefix("~").lowercase(Locale.ROOT)
            if (!isPlainDomain(domain)) return null
            if (isExclusion) exclude += domain else include += domain
        }
        return include to exclude
    }

    private fun isPlainDomain(domain: String): Boolean {
        val isPlainHost = domain.isNotEmpty() && domain.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '-' }
        return isPlainHost && !domain.startsWith(".") && !domain.endsWith(".") && ".." !in domain
    }
}

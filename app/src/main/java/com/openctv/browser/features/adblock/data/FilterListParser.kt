package com.openctv.browser.features.adblock.data

import com.openctv.browser.features.adblock.domain.CompiledFilters
import com.openctv.browser.features.adblock.domain.NetworkRule
import java.util.Locale

/**
 * Parse une liste au format EasyList / Adblock Plus, orientée filtrage réseau.
 *
 * Supporté : `||domaine^` (et `||domaine`), exceptions `@@||domaine^`, options
 * `third-party`/`3p`, `~third-party`/`~3p`, `domain=a.com|~b.com`, `important`.
 *
 * Ignoré (sans erreur) : commentaires (`!`, en-tête `[Adblock`), règles cosmétiques
 * (`##`, `#@#`, `#?#`, `#$#`), règles à chemin (`||d.com/path`), motifs génériques ou regex,
 * règles avec une autre option (types de ressource, `csp`, `redirect`…) : les appliquer au
 * domaine entier bloquerait trop. Toute ligne invalide est ignorée.
 */
object FilterListParser {

    private val cosmeticMarkers = listOf("##", "#@#", "#?#", "#$#", "#@?#", "#@$#")
    private val acceptedFlagOptions = setOf("important")

    fun parse(content: String): CompiledFilters = parse(content.lineSequence())

    fun parse(lines: Sequence<String>): CompiledFilters =
        CompiledFilters.of(lines.mapNotNull { parseLine(it) }.asIterable())

    fun parseLine(raw: String): NetworkRule? = try {
        parseRule(raw.trim())
    } catch (_: RuntimeException) {
        null
    }

    private fun parseRule(line: String): NetworkRule? {
        if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return null
        if (cosmeticMarkers.any { it in line }) return null

        val isException = line.startsWith("@@")
        val body = if (isException) line.substring(2) else line
        if (!body.startsWith("||")) return null

        val optionsIndex = body.lastIndexOf('$')
        val pattern = if (optionsIndex >= 0) body.substring(2, optionsIndex) else body.substring(2)
        val optionsText = if (optionsIndex >= 0) body.substring(optionsIndex + 1) else ""

        val domain = extractDomain(pattern) ?: return null
        val options = parseOptions(optionsText) ?: return null
        return NetworkRule(
            domain = domain,
            isException = isException,
            thirdPartyOnly = options.thirdPartyOnly,
            firstPartyOnly = options.firstPartyOnly,
            includePageDomains = options.include,
            excludePageDomains = options.exclude,
        )
    }

    /** Le motif doit être `domaine` ou `domaine^` : pas de chemin, de joker ni de port. */
    private fun extractDomain(pattern: String): String? {
        val domain = pattern.removeSuffix("^").lowercase(Locale.ROOT)
        val isPlainHost = domain.isNotEmpty() && domain.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '-' }
        val hasValidLabels = isPlainHost && !domain.startsWith(".") && !domain.endsWith(".") && ".." !in domain
        return domain.takeIf { hasValidLabels }
    }

    private class Options(
        val thirdPartyOnly: Boolean = false,
        val firstPartyOnly: Boolean = false,
        val include: Set<String> = emptySet(),
        val exclude: Set<String> = emptySet(),
    )

    /** `null` si une option non supportée rend la règle inapplicable telle quelle. */
    private fun parseOptions(text: String): Options? {
        if (text.isEmpty()) return Options()
        var thirdPartyOnly = false
        var firstPartyOnly = false
        val include = mutableSetOf<String>()
        val exclude = mutableSetOf<String>()
        for (option in text.split(',')) {
            val name = option.trim().lowercase(Locale.ROOT)
            when {
                name == "third-party" || name == "3p" -> thirdPartyOnly = true
                name == "~third-party" || name == "~3p" -> firstPartyOnly = true
                name in acceptedFlagOptions -> Unit
                name.startsWith("domain=") -> {
                    for (entry in name.removePrefix("domain=").split('|')) {
                        if (entry.isEmpty()) return null
                        if (entry.startsWith("~")) exclude += entry.substring(1) else include += entry
                    }
                }
                else -> return null
            }
        }
        return Options(thirdPartyOnly, firstPartyOnly, include, exclude)
    }
}

package com.openctv.browser.core.webview

/**
 * Liste blanche stricte des sélecteurs CSS que [CosmeticStyleScript] accepte d'injecter dans une page. Une règle
 * cosmétique vient d'une liste téléchargée (donc non fiable) : tout ce qui n'est pas explicitement reconnu est refusé.
 *
 * Accepté : sélecteurs de type, classe, id, attribut (`[a="b"]`, `^=`, `$=`, `*=`, `~=`, `|=`), combinateurs
 * (` `, `>`, `+`, `~`), `*`, et les seules pseudo-classes de [AllowedPseudoClasses]. Au plus [MaxLength] caractères.
 *
 * Refusé : `{ } ; @ \ < / !`, tout caractère hors de [isAllowedChar] (dont les non ASCII), `url(`, `expression`,
 * `javascript`, `import`, pseudo-éléments (`::`), pseudo-classes inconnues (`:has-text`, `:xpath`, `:-abp-…`),
 * fonctions hors pseudo-classes autorisées (`+js(…)`), crochets, parenthèses ou guillemets mal appariés, et les
 * sélecteurs qui masqueraient toute la page (`*`, `html`, `body`, `head`, `:root`).
 */
object CosmeticSelector {

    const val MaxLength = 256
    private const val MaxParenthesesDepth = 3

    private val AllowedPseudoClasses = setOf(
        "not", "is", "where", "has",
        "nth-child", "nth-last-child", "nth-of-type", "nth-last-of-type",
        "first-child", "last-child", "first-of-type", "last-of-type", "only-child", "only-of-type",
    )
    private val FunctionalPseudoClasses = setOf(
        "not", "is", "where", "has", "nth-child", "nth-last-child", "nth-of-type", "nth-last-of-type",
    )
    private val ForbiddenWords = listOf("url(", "expression", "javascript", "import")
    private val WholePageSelectors = setOf("*", "html", "body", "head", ":root")

    fun isSafe(selector: String): Boolean {
        val isTrimmed = selector == selector.trim()
        val hasValidLength = selector.isNotEmpty() && selector.length <= MaxLength
        if (!isTrimmed || !hasValidLength) return false
        if (!selector.all(::isAllowedChar)) return false
        val lowered = selector.lowercase()
        if (ForbiddenWords.any { it in lowered }) return false
        if (lowered in WholePageSelectors) return false
        return hasBalancedStructure(selector)
    }

    /** Découpe une liste `a, b` sur les virgules de premier niveau (pas celles de `:not(a, b)` ni d'un attribut). */
    fun splitList(text: String): List<String> {
        val parts = ArrayList<String>(1)
        var depth = 0
        var quote = NoQuote
        var start = 0
        for (i in text.indices) {
            val c = text[i]
            when {
                quote != NoQuote -> if (c == quote) quote = NoQuote
                c == '"' || c == '\'' -> quote = c
                c == '(' || c == '[' -> depth++
                c == ')' || c == ']' -> depth--
                c == ',' && depth == 0 -> {
                    parts += text.substring(start, i).trim()
                    start = i + 1
                }
            }
        }
        parts += text.substring(start).trim()
        return parts
    }

    private const val NoQuote = '\u0000'

    private fun isAllowedChar(c: Char): Boolean =
        c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in " _-.#>+~*:[]()=\"'^$|,"

    private fun isIdentifierChar(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-'

    /** Crochets, parenthèses et guillemets appariés ; `(` et `:` seulement derrière une pseudo-classe autorisée. */
    private fun hasBalancedStructure(selector: String): Boolean {
        var quote = NoQuote
        var isInAttribute = false
        var parenthesesDepth = 0
        var i = 0
        while (i < selector.length) {
            val c = selector[i]
            if (quote != NoQuote) {
                if (c == quote) quote = NoQuote
                i++
                continue
            }
            when (c) {
                '"', '\'' -> {
                    if (!isInAttribute) return false
                    quote = c
                }
                '[' -> {
                    if (isInAttribute) return false
                    isInAttribute = true
                }
                ']' -> {
                    if (!isInAttribute) return false
                    isInAttribute = false
                }
                ':' -> if (!isInAttribute) {
                    val name = identifierAfter(selector, i + 1)
                    if (name !in AllowedPseudoClasses) return false
                    val opensFunction = selector.getOrNull(i + 1 + name.length) == '('
                    if (opensFunction != (name in FunctionalPseudoClasses)) return false
                }
                '(' -> if (!isInAttribute) {
                    parenthesesDepth++
                    if (parenthesesDepth > MaxParenthesesDepth || !isAfterFunctionalPseudoClass(selector, i)) return false
                }
                ')' -> if (!isInAttribute) {
                    parenthesesDepth--
                    if (parenthesesDepth < 0) return false
                }
            }
            i++
        }
        return quote == NoQuote && !isInAttribute && parenthesesDepth == 0
    }

    private fun identifierAfter(selector: String, start: Int): String {
        var end = start
        while (end < selector.length && isIdentifierChar(selector[end])) end++
        return selector.substring(start, end).lowercase()
    }

    private fun isAfterFunctionalPseudoClass(selector: String, openIndex: Int): Boolean {
        var start = openIndex
        while (start > 0 && isIdentifierChar(selector[start - 1])) start--
        val hasColon = start > 0 && selector[start - 1] == ':'
        val isPseudoElement = start > 1 && selector[start - 2] == ':'
        val name = selector.substring(start, openIndex).lowercase()
        return hasColon && !isPseudoElement && name in FunctionalPseudoClasses
    }
}

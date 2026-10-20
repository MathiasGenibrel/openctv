package com.openctv.browser.features.search.domain

import com.openctv.browser.features.settings.domain.SearchEngine

/**
 * Décide si la saisie est une adresse ou une recherche. Pur et local : rien n'est envoyé au réseau.
 *
 * - `https://…` / `http://…` : adresse telle quelle ;
 * - `arte.tv`, `arte.tv/replay` : adresse, ouverte en `https` ;
 * - `localhost`, `localhost:8080`, IPv4 (`192.168.1.10:3000`) : adresse, ouverte en `http` (pas de TLS en local) ;
 * - tout le reste (mots, espaces, autres schémas comme `javascript:` ou `file://`) : recherche.
 *
 * Seuls `http`/`https` sont donc jamais produits.
 */
object InputResolver {
    private val ExplicitScheme = Regex("^([A-Za-z][A-Za-z0-9+.-]*)://")
    private val Whitespace = Regex("\\s+")
    private val WebSchemes = setOf("http", "https")
    private const val MaxPort = 65535

    /** `null` si la saisie est vide. */
    fun resolve(raw: String, engine: SearchEngine): ResolvedInput? {
        val input = raw.trim()
        if (input.isEmpty()) return null
        val address = asAddress(input)
        if (address != null) return ResolvedInput.Url(address)
        val query = input.replace(Whitespace, " ")
        return ResolvedInput.Search(query, engine.searchUrl(query))
    }

    private fun asAddress(input: String): String? {
        val hasWhitespace = input.any(Char::isWhitespace)
        if (hasWhitespace) return null

        val scheme = ExplicitScheme.find(input)
        if (scheme != null) return asExplicitWebAddress(input, scheme)

        val authority = authorityOf(input)
        val host = hostOf(authority) ?: return null
        val isLocal = host == "localhost" || isIpv4(host)
        val defaultScheme = if (isLocal) "http" else "https"
        return "$defaultScheme://$input"
    }

    private fun asExplicitWebAddress(input: String, scheme: MatchResult): String? {
        val isWebScheme = scheme.groupValues[1].lowercase() in WebSchemes
        if (!isWebScheme) return null
        val authority = authorityOf(input.substring(scheme.range.last + 1))
        val hasHost = authority.isNotEmpty() && '@' !in authority && hostOf(authority, lenient = true) != null
        return if (hasHost) input else null
    }

    private fun authorityOf(text: String): String = text.takeWhile { it != '/' && it != '?' && it != '#' }

    /** Hôte de `host[:port]` si l'autorité est valide (port compris), sinon `null`. */
    private fun hostOf(authority: String, lenient: Boolean = false): String? {
        val hasUserInfo = '@' in authority
        if (hasUserInfo) return null
        val separator = authority.lastIndexOf(':')
        val host = if (separator >= 0) authority.substring(0, separator) else authority
        val port = if (separator >= 0) authority.substring(separator + 1) else null
        val isPortValid = port == null || isPort(port)
        if (!isPortValid) return null
        val isHostValid = host == "localhost" || isIpv4(host) || isDomain(host) || (lenient && isHostName(host))
        return host.takeIf { isHostValid }
    }

    private fun isPort(text: String): Boolean {
        val isNumeric = text.isNotEmpty() && text.all { it in '0'..'9' }
        return isNumeric && text.length <= 5 && text.toInt() in 1..MaxPort
    }

    private fun isIpv4(host: String): Boolean {
        val octets = host.split('.')
        val hasFourOctets = octets.size == 4
        return hasFourOctets && octets.all { octet ->
            val isNumeric = octet.isNotEmpty() && octet.length <= 3 && octet.all { it in '0'..'9' }
            isNumeric && octet.toInt() <= 255
        }
    }

    /** Nom de domaine : au moins deux libellés, extension alphabétique d'au moins 2 lettres. */
    private fun isDomain(host: String): Boolean {
        val labels = host.split('.')
        val hasExtension = labels.size >= 2
        if (!hasExtension || !labels.all(::isLabel)) return false
        val extension = labels.last()
        val isPunycode = extension.startsWith("xn--")
        val isAlphabetic = extension.length >= 2 && extension.all(Char::isLetter)
        return isAlphabetic || isPunycode
    }

    /** Hôte d'une adresse explicite (`http://intranet/`) : un seul libellé accepté. */
    private fun isHostName(host: String): Boolean = host.split('.').all(::isLabel)

    private fun isLabel(label: String): Boolean {
        val isNotEmpty = label.isNotEmpty()
        val hasValidEdges = !label.startsWith('-') && !label.endsWith('-')
        val hasValidChars = label.all { it.isLetterOrDigit() || it == '-' }
        return isNotEmpty && hasValidEdges && hasValidChars
    }
}

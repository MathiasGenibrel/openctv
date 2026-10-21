package com.openctv.browser.core.security

/*
 * Seule source de vérité pour « cet hôte est-il du réseau local ? ». Deux questions voisines, volontairement
 * distinctes :
 *
 * | Hôte | [isHttpByDefaultHost] | [isLocalNetworkHost] |
 * |---|---|---|
 * | `localhost`, extension privée (`gitlab.tools.internal`, `nas.local`…) | oui | oui |
 * | IPv4 privée ou de bouclage (`192.168.1.10`, `127.0.0.1`) | oui | oui |
 * | IPv4 publique (`8.8.8.8`) | oui : une IP saisie désigne en pratique un appareil (comportement historique) | non |
 * | nom sans point (`nas`), IPv6 privée (`[::1]`) | non : la barre d'adresse n'en fait pas une adresse sans schéma | oui |
 * | tout le reste (`internal.evil.com`) | non | non |
 *
 * Les deux prédicats ignorent la casse et le `.` final (`GitLab.Tools.Internal.`). L'extension doit être le
 * dernier libellé : `evil.com.internal` est un nom privé (il ne se résout que sur le réseau local), mais
 * `internal.evil.com` ne l'est pas. HTTP par défaut ne retire rien à l'avertissement HTTP, toujours affiché.
 */

/** Extensions réservées aux réseaux privés (RFC 6761/6762/8375 et usages courants des box et intranets). */
private val PrivateSuffixes = listOf(".internal", ".local", ".lan", ".home", ".home.arpa", ".localdomain")

private const val Localhost = "localhost"

/**
 * [host] désigne-t-il un appareil du réseau local (courant avec un certificat auto-signé) plutôt qu'un site
 * public ? Sert la note « réseau local » de l'avertissement de sécurité.
 */
fun isLocalNetworkHost(host: String?): Boolean {
    val name = host?.let(::normalized) ?: return false
    val isSingleLabel = name.none { it == '.' || it == ':' }
    val isLocalName = name == Localhost || isSingleLabel || hasPrivateNetworkSuffix(name)
    val isPrivateAddress = ipv4Octets(name)?.let(::isPrivateIpv4) == true || isPrivateIpv6(name)
    return isLocalName || isPrivateAddress
}

/**
 * Un site de cet hôte s'ouvre-t-il en `http://` quand l'utilisateur ne précise pas de schéma ? Pas de
 * certificat public possible pour ces hôtes, donc pas de HTTPS. Tout le reste : HTTPS.
 */
fun isHttpByDefaultHost(host: String): Boolean {
    val name = normalized(host)
    val isIpv4 = ipv4Octets(name) != null
    return name == Localhost || isIpv4 || hasPrivateNetworkSuffix(name)
}

private fun normalized(host: String): String = host.lowercase().removeSuffix(".")

private fun hasPrivateNetworkSuffix(host: String): Boolean = PrivateSuffixes.any { host.endsWith(it) }

/** Octets d'une IPv4 décimale `a.b.c.d`, `null` pour tout autre hôte. */
private fun ipv4Octets(host: String): List<Int>? {
    val parts = host.split('.')
    val hasFourParts = parts.size == 4
    val areDecimalOctets = parts.all { part ->
        val isNumeric = part.isNotEmpty() && part.length <= 3 && part.all { it in '0'..'9' }
        isNumeric && part.toInt() <= 255
    }
    return if (hasFourParts && areDecimalOctets) parts.map(String::toInt) else null
}

private fun isPrivateIpv4(octets: List<Int>): Boolean {
    val (first, second) = octets
    return first == 10 || first == 127 || (first == 192 && second == 168) ||
        (first == 172 && second in 16..31) || (first == 169 && second == 254)
}

private fun isPrivateIpv6(host: String): Boolean {
    val address = host.removePrefix("[").removeSuffix("]")
    val isIpv6 = host.startsWith("[")
    return isIpv6 && (address == "::1" || address.startsWith("fc") || address.startsWith("fd") || address.startsWith("fe80"))
}

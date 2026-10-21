package com.openctv.browser.core.security

/**
 * [host] désigne-t-il un appareil du réseau local (courant avec un certificat auto-signé) plutôt qu'un site
 * public ? `localhost`, noms sans point, `.local` `.lan` `.home` `.internal`, adresses privées ou locales.
 */
fun isLocalNetworkHost(host: String?): Boolean {
    val name = host ?: return false
    val isLocalName = name == "localhost" || name.none { it == '.' || it == ':' } ||
        hasPrivateNetworkSuffix(name)
    return isLocalName || isPrivateIpv4(name) || isPrivateIpv6(name)
}

/** Extensions réservées aux réseaux privés (RFC 6761/6762/8375 et usages courants des box et intranets). */
private val PrivateSuffixes = listOf(".internal", ".local", ".lan", ".home", ".home.arpa", ".localdomain")

/** `host` se termine par une extension réservée aux réseaux privés (`gitlab.tools.internal`, `nas.local`). */
fun hasPrivateNetworkSuffix(host: String): Boolean = PrivateSuffixes.any { host.endsWith(it) }

/**
 * Un site de cet hôte s'ouvre-t-il en `http://` quand l'utilisateur ne précise pas de schéma ? `localhost`,
 * IPv4 et noms de réseau privé : pas de certificat public possible, donc pas de HTTPS. Tout le reste : HTTPS.
 */
fun isHttpByDefaultHost(host: String): Boolean {
    val isIpv4 = host.split('.').let { parts ->
        parts.size == 4 && parts.all { it.isNotEmpty() && it.length <= 3 && it.all(Char::isDigit) && it.toInt() <= 255 }
    }
    return host == "localhost" || isIpv4 || hasPrivateNetworkSuffix(host)
}

private fun isPrivateIpv4(host: String): Boolean {
    val parts = host.split('.')
    val octets = parts.mapNotNull { it.toIntOrNull()?.takeIf { value -> value in 0..255 } }
    val isIpv4 = parts.size == 4 && octets.size == 4
    if (!isIpv4) return false
    val (first, second) = octets
    return first == 10 || first == 127 || (first == 192 && second == 168) ||
        (first == 172 && second in 16..31) || (first == 169 && second == 254)
}

private fun isPrivateIpv6(host: String): Boolean {
    val address = host.removePrefix("[").removeSuffix("]")
    val isIpv6 = host.startsWith("[")
    return isIpv6 && (address == "::1" || address.startsWith("fc") || address.startsWith("fd") || address.startsWith("fe80"))
}

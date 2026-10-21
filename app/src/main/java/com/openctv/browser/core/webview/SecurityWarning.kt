package com.openctv.browser.core.webview

import com.openctv.browser.core.security.SiteException
import java.time.Instant

/** Pourquoi un certificat HTTPS est refusé (traduit de `SslError.getPrimaryError()`, cf. [certificateProblemOf]). */
enum class CertificateProblem { Expired, NameMismatch, Untrusted, NotYetValid, Invalid }

/**
 * Traduit `SslError.getPrimaryError()` (valeurs constantes de l'API Android) : `SSL_NOTYETVALID` 0,
 * `SSL_EXPIRED` 1, `SSL_IDMISMATCH` 2, `SSL_UNTRUSTED` 3, `SSL_DATE_INVALID` 4, `SSL_INVALID` 5.
 */
internal fun certificateProblemOf(primaryError: Int): CertificateProblem = when (primaryError) {
    0 -> CertificateProblem.NotYetValid
    1 -> CertificateProblem.Expired
    2 -> CertificateProblem.NameMismatch
    3 -> CertificateProblem.Untrusted
    else -> CertificateProblem.Invalid
}

/**
 * Avertissement de sécurité affiché à la place de la page principale. L'utilisateur peut le contourner en
 * accordant la [exception] correspondante ; sans elle (`null`), il n'y a pas d'issue autre que le retour.
 */
sealed interface SecurityWarning {
    /** Adresse de la page refusée. */
    val url: String

    /** Hôte exact ([com.openctv.browser.core.security.SiteHost]), `null` s'il n'est pas lisible sans ambiguïté. */
    val host: String?

    /** Exception à accorder pour afficher la page ; `null` quand elle ne peut pas être décrite sûrement. */
    val exception: SiteException?

    /** Page en HTTP : rien n'est chiffré. */
    data class InsecureHttp(override val url: String, override val host: String?) : SecurityWarning {
        override val exception: SiteException? get() = host?.let(SiteException::Http)
    }

    /** Certificat HTTPS invalide ; [fingerprint] (SHA-256) lie l'exception à CE certificat. */
    data class InvalidCertificate(
        override val url: String,
        override val host: String?,
        val problem: CertificateProblem,
        val issuedTo: String?,
        val validUntil: Instant?,
        val fingerprint: String?,
    ) : SecurityWarning {
        override val exception: SiteException?
            get() = if (host != null && fingerprint != null) SiteException.Certificate(host, fingerprint) else null
    }
}

/**
 * [host] désigne-t-il un appareil du réseau local (courant avec un certificat auto-signé) plutôt qu'un site
 * public ? `localhost`, noms sans point, `.local` `.lan` `.home` `.internal`, adresses privées ou locales.
 */
fun isLocalNetworkHost(host: String?): Boolean {
    val name = host ?: return false
    val isLocalName = name == "localhost" || name.none { it == '.' || it == ':' } ||
        LocalSuffixes.any { name.endsWith(it) }
    return isLocalName || isPrivateIpv4(name) || isPrivateIpv6(name)
}

private val LocalSuffixes = listOf(".local", ".lan", ".home", ".internal", ".localdomain")

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

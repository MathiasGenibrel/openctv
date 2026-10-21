package com.openctv.browser.core.webview

import com.openctv.browser.core.security.SiteException
import java.time.Instant

/** Pourquoi un certificat HTTPS est refusé (traduit de `SslError.getPrimaryError()`, cf. [certificateProblemOf]). */
enum class CertificateProblem { Expired, NameMismatch, Untrusted, NotYetValid, Invalid }

/**
 * Traduit `SslError.getPrimaryError()` (valeurs constantes de l'API Android) : `SSL_NOTYETVALID` 0,
 * `SSL_EXPIRED` 1, `SSL_IDMISMATCH` 2, `SSL_UNTRUSTED` 3, `SSL_DATE_INVALID` 4, `SSL_INVALID` 5.
 * `SSL_DATE_INVALID` est départagé par les dates du certificat ([validNotBefore], [validNotAfter]) et l'heure [now].
 */
internal fun certificateProblemOf(
    primaryError: Int,
    validNotBefore: Instant? = null,
    validNotAfter: Instant? = null,
    now: Instant = Instant.now(),
): CertificateProblem = when (primaryError) {
    SSL_DATE_INVALID -> dateProblemOf(validNotBefore, validNotAfter, now)
    0 -> CertificateProblem.NotYetValid
    1 -> CertificateProblem.Expired
    2 -> CertificateProblem.NameMismatch
    3 -> CertificateProblem.Untrusted
    else -> CertificateProblem.Invalid
}

private const val SSL_DATE_INVALID = 4

/** `SSL_DATE_INVALID` ne dit pas de quel côté : les dates du certificat départagent expiré / pas encore valide. */
private fun dateProblemOf(validNotBefore: Instant?, validNotAfter: Instant?, now: Instant): CertificateProblem {
    val isExpired = validNotAfter != null && validNotAfter.isBefore(now)
    val isNotYetValid = validNotBefore != null && validNotBefore.isAfter(now)
    return when {
        isExpired -> CertificateProblem.Expired
        isNotYetValid -> CertificateProblem.NotYetValid
        else -> CertificateProblem.Invalid
    }
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

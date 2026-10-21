package com.openctv.browser.core.security

import java.security.MessageDigest

/**
 * Exception de sécurité choisie par l'utilisateur pour un site : seule manière, avec l'avertissement qui la
 * précède, d'afficher une page HTTP ou de continuer malgré un certificat invalide.
 * - [Http] : cet hôte peut être affiché en HTTP ;
 * - [Certificate] : cet hôte peut être affiché avec CE certificat (empreinte SHA-256). Un autre certificat pour
 *   le même hôte ne la satisfait pas : l'avertissement revient.
 *
 * L'exception porte sur l'hôte exact (cf. [SiteHost]) : `www.nas.maison` et `nas.maison` sont deux sites.
 */
sealed interface SiteException {
    val host: String

    data class Http(override val host: String) : SiteException

    data class Certificate(override val host: String, val fingerprint: String) : SiteException
}

/** Forme stockée d'une exception (`http|hôte`, `cert|hôte|empreinte`) ; une entrée illisible est ignorée. */
object SiteExceptionCodec {
    private const val SEPARATOR = '|'

    fun encode(exception: SiteException): String = when (exception) {
        is SiteException.Http -> "http$SEPARATOR${exception.host}"
        is SiteException.Certificate -> "cert$SEPARATOR${exception.host}$SEPARATOR${exception.fingerprint}"
    }

    fun decode(stored: String): SiteException? {
        val parts = stored.split(SEPARATOR)
        val host = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
        return when {
            host == null -> null
            parts[0] == "http" && parts.size == 2 -> SiteException.Http(host)
            parts[0] == "cert" && parts.size == 3 && parts[2].isNotEmpty() -> SiteException.Certificate(host, parts[2])
            else -> null
        }
    }
}

/** Empreinte SHA-256 d'un certificat : hexadécimal minuscule, sans séparateur (forme stockée et comparée). */
object CertificateFingerprint {
    fun sha256(encodedCertificate: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(encodedCertificate).joinToString("") { "%02x".format(it) }

    /** Début lisible d'une empreinte pour l'affichage : `ab:cd:ef:01:23:45:67:89…`. */
    fun abbreviated(fingerprint: String, bytes: Int = AbbreviatedBytes): String {
        val shown = fingerprint.chunked(2).take(bytes).joinToString(":")
        val isTruncated = fingerprint.length > bytes * 2
        return if (isTruncated) "$shown…" else shown
    }

    private const val AbbreviatedBytes = 8
}

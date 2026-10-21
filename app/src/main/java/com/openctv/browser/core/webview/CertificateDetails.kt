package com.openctv.browser.core.webview

import android.net.http.SslCertificate
import android.os.Build
import com.openctv.browser.core.security.CertificateFingerprint
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant

/** Ce qu'on lit d'un certificat présenté par un site : pour l'écran d'avertissement et l'empreinte de l'exception. */
internal data class CertificateDetails(
    val issuedTo: String?,
    val validUntil: Instant?,
    /** SHA-256 du certificat (cf. [CertificateFingerprint]) ; `null` si le certificat n'a pas pu être lu : aucune exception possible. */
    val fingerprint: String?,
) {
    companion object {
        val Unreadable = CertificateDetails(issuedTo = null, validUntil = null, fingerprint = null)

        fun of(certificate: SslCertificate?): CertificateDetails {
            certificate ?: return Unreadable
            val x509 = x509Of(certificate)
            return CertificateDetails(
                issuedTo = certificate.issuedTo?.cName?.takeIf { it.isNotBlank() },
                validUntil = x509?.notAfter?.toInstant(),
                fingerprint = x509?.let { runCatching { CertificateFingerprint.sha256(it.encoded) }.getOrNull() },
            )
        }

        /** `getX509Certificate()` existe depuis l'API 29 ; en dessous (minSdk 28), on relit le DER sauvegardé par `saveState`. */
        private fun x509Of(certificate: SslCertificate): X509Certificate? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                certificate.x509Certificate
            } else {
                runCatching {
                    val der = SslCertificate.saveState(certificate).getByteArray(X509_STATE_KEY) ?: return null
                    CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der)) as? X509Certificate
                }.getOrNull()
            }

        private const val X509_STATE_KEY = "x509-certificate"
    }
}

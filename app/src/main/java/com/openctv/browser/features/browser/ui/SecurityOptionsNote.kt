package com.openctv.browser.features.browser.ui

import androidx.annotation.StringRes
import com.openctv.browser.R
import com.openctv.browser.core.security.isLocalNetworkHost

/**
 * Note affichée dans « Options avancées » d'un avertissement de sécurité : rassurante pour un appareil du
 * réseau local (HTTP / certificat auto-signé courants), plus ferme pour un site public. Le texte reçoit
 * l'hôte en premier argument de format.
 */
internal enum class SecurityOptionsNote(@StringRes val textRes: Int) {
    HttpLocalNetwork(R.string.security_http_options_local),
    HttpPublic(R.string.security_http_options_public),
    CertificateLocalNetwork(R.string.security_cert_options_local),
    CertificatePublic(R.string.security_cert_options_public),
}

internal fun securityOptionsNote(isCertificate: Boolean, host: String?): SecurityOptionsNote {
    val isLocal = isLocalNetworkHost(host)
    return when {
        isCertificate && isLocal -> SecurityOptionsNote.CertificateLocalNetwork
        isCertificate -> SecurityOptionsNote.CertificatePublic
        isLocal -> SecurityOptionsNote.HttpLocalNetwork
        else -> SecurityOptionsNote.HttpPublic
    }
}

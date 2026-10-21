package com.openctv.browser.features.browser.ui

import androidx.annotation.StringRes
import com.openctv.browser.R
import com.openctv.browser.core.security.isLocalNetworkHost
import com.openctv.browser.core.webview.SecurityWarning

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

/** Note des options avancées de cet avertissement, selon son type et l'hôte (local ou public). */
internal fun SecurityWarning.optionsNote(): SecurityOptionsNote {
    val isLocal = isLocalNetworkHost(host)
    return when (this) {
        is SecurityWarning.InsecureHttp ->
            if (isLocal) SecurityOptionsNote.HttpLocalNetwork else SecurityOptionsNote.HttpPublic
        is SecurityWarning.InvalidCertificate ->
            if (isLocal) SecurityOptionsNote.CertificateLocalNetwork else SecurityOptionsNote.CertificatePublic
    }
}

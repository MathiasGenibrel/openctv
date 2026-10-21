package com.openctv.browser.features.browser.ui

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.webview.CertificateProblem
import com.openctv.browser.core.webview.SecurityWarning
import org.junit.Test

class SecurityOptionsNoteTest {

    private fun http(host: String?) = SecurityWarning.InsecureHttp(url = "http://${host ?: "?"}/", host = host)

    private fun certificate(host: String) = SecurityWarning.InvalidCertificate(
        url = "https://$host/",
        host = host,
        problem = CertificateProblem.Untrusted,
        issuedTo = host,
        validUntil = null,
        fingerprint = "ab12",
    )

    @Test
    fun `Given an HTTP warning on a private host When options open Then the local network note is shown`() {
        assertThat(http("gitlab-dashboard.tools.internal").optionsNote()).isEqualTo(SecurityOptionsNote.HttpLocalNetwork)
        assertThat(http("192.168.1.10").optionsNote()).isEqualTo(SecurityOptionsNote.HttpLocalNetwork)
    }

    @Test
    fun `Given an HTTP warning on a public host When options open Then the firmer note is shown`() {
        assertThat(http("example.org").optionsNote()).isEqualTo(SecurityOptionsNote.HttpPublic)
        assertThat(http("internal.evil.com").optionsNote()).isEqualTo(SecurityOptionsNote.HttpPublic)
        assertThat(http(null).optionsNote()).isEqualTo(SecurityOptionsNote.HttpPublic)
    }

    @Test
    fun `Given an invalid certificate When options open Then the note depends on the host being local`() {
        assertThat(certificate("nas.local").optionsNote()).isEqualTo(SecurityOptionsNote.CertificateLocalNetwork)
        assertThat(certificate("example.org").optionsNote()).isEqualTo(SecurityOptionsNote.CertificatePublic)
    }
}

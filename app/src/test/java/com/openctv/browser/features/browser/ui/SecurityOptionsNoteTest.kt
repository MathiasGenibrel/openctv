package com.openctv.browser.features.browser.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SecurityOptionsNoteTest {

    @Test
    fun `Given an HTTP warning on a private host When options open Then the local network note is shown`() {
        assertThat(securityOptionsNote(isCertificate = false, host = "gitlab-dashboard.tools.internal"))
            .isEqualTo(SecurityOptionsNote.HttpLocalNetwork)
        assertThat(securityOptionsNote(isCertificate = false, host = "192.168.1.10"))
            .isEqualTo(SecurityOptionsNote.HttpLocalNetwork)
    }

    @Test
    fun `Given an HTTP warning on a public host When options open Then the firmer note is shown`() {
        assertThat(securityOptionsNote(isCertificate = false, host = "example.org"))
            .isEqualTo(SecurityOptionsNote.HttpPublic)
        assertThat(securityOptionsNote(isCertificate = false, host = "internal.evil.com"))
            .isEqualTo(SecurityOptionsNote.HttpPublic)
        assertThat(securityOptionsNote(isCertificate = false, host = null))
            .isEqualTo(SecurityOptionsNote.HttpPublic)
    }

    @Test
    fun `Given an invalid certificate When options open Then the note depends on the host being local`() {
        assertThat(securityOptionsNote(isCertificate = true, host = "nas.local"))
            .isEqualTo(SecurityOptionsNote.CertificateLocalNetwork)
        assertThat(securityOptionsNote(isCertificate = true, host = "example.org"))
            .isEqualTo(SecurityOptionsNote.CertificatePublic)
    }
}

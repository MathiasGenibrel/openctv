package com.openctv.browser.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SiteExceptionTest {

    @Test
    fun `Given stored exceptions When they are read back Then they are the same exceptions`() {
        val exceptions = listOf(SiteException.Http("192.168.1.10"), SiteException.Certificate("[::1]", "ab12"))

        exceptions.forEach { assertThat(SiteExceptionCodec.decode(SiteExceptionCodec.encode(it))).isEqualTo(it) }
    }

    @Test
    fun `Given corrupted stored entries When they are read Then they are ignored rather than trusted`() {
        listOf("", "http", "http|", "cert|host", "cert|host|", "cert|host|ab|extra", "other|host", "http|host|x")
            .forEach { assertThat(SiteExceptionCodec.decode(it)).isNull() }
    }

    @Test
    fun `Given the same bytes When the certificate fingerprint is computed Then it is the lowercase SHA-256 hex`() {
        val fingerprint = CertificateFingerprint.sha256("abc".toByteArray())

        assertThat(fingerprint).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        assertThat(CertificateFingerprint.sha256("abd".toByteArray())).isNotEqualTo(fingerprint)
    }

    @Test
    fun `Given a long fingerprint When it is listed in Settings Then only its start is shown`() {
        val fingerprint = CertificateFingerprint.sha256("abc".toByteArray())

        assertThat(CertificateFingerprint.abbreviated(fingerprint)).isEqualTo("ba:78:16:bf:8f:01:cf:ea…")
        assertThat(CertificateFingerprint.abbreviated("ab12")).isEqualTo("ab:12")
    }
}

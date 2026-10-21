package com.openctv.browser.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SiteHostTest {

    @Test
    fun `Given various addresses When the host is read Then it is lowercase without port credentials or trailing dot`() {
        assertThat(SiteHost.of("http://192.168.1.10:8080/admin?x=1")).isEqualTo("192.168.1.10")
        assertThat(SiteHost.of("HTTPS://NAS.Maison./")).isEqualTo("nas.maison")
        assertThat(SiteHost.of("http://user:pw@my_nas.lan/")).isEqualTo("my_nas.lan")
        assertThat(SiteHost.of("http://[::1]:3000/")).isEqualTo("[::1]")
    }

    @Test
    fun `Given a www address When the host is read Then www is kept because the exception targets the exact host`() {
        assertThat(SiteHost.of("https://www.nas.maison/")).isEqualTo("www.nas.maison")
        assertThat(SiteHost.of("https://www.nas.maison/")).isNotEqualTo(SiteHost.of("https://nas.maison/"))
    }

    @Test
    fun `Given a backslash or credential trick When the host is read Then it is the host the browser really contacts`() {
        assertThat(SiteHost.of("http://evil.com\\@good.com/")).isEqualTo("evil.com")
        assertThat(SiteHost.of("http://good.com@evil.com/")).isEqualTo("evil.com")
        assertThat(SiteHost.of("http://evil.com#@good.com/")).isEqualTo("evil.com")
        assertThat(SiteHost.of("http://evil.com?@good.com/")).isEqualTo("evil.com")
    }

    @Test
    fun `Given an address with an ambiguous host When the host is read Then it is refused`() {
        listOf("http://exa%6Dple.com/", "http://exa mple.com/", "http://café.fr/", "http:///path", "example.org", "", null)
            .forEach { assertThat(SiteHost.of(it)).isNull() }
    }

    @Test
    fun `Given an IPv4 address written in a form Chromium recomputes When the host is read Then it is refused`() {
        listOf(
            "http://3232235786/", "http://0xc0a8010a/", "http://192.168.010.010/", "http://192.168.1/",
            "http://0x7f.1/", "http://192.168.1.256/", "http://example.123/",
        ).forEach { assertThat(SiteHost.of(it)).isNull() }
    }

    @Test
    fun `Given a canonical IPv4 address or a name with digits When the host is read Then it is kept`() {
        assertThat(SiteHost.of("http://192.168.1.10/")).isEqualTo("192.168.1.10")
        assertThat(SiteHost.of("http://0.0.0.0/")).isEqualTo("0.0.0.0")
        assertThat(SiteHost.of("https://1password.com/")).isEqualTo("1password.com")
        assertThat(SiteHost.of("https://box2.lan/")).isEqualTo("box2.lan")
    }

    @Test
    fun `Given text after the host that is not a numeric port When the host is read Then it is refused`() {
        listOf("http://[::1]evil.com/", "http://[::1/", "http://nas.lan:80x/", "http://nas.lan:80:90/")
            .forEach { assertThat(SiteHost.of(it)).isNull() }
        assertThat(SiteHost.of("http://nas.lan:/")).isEqualTo("nas.lan")
    }

    @Test
    fun `Given the same host on two ports or in upper case punycode When the host is read Then it designates one site`() {
        assertThat(SiteHost.of("http://nas.lan:8080/")).isEqualTo(SiteHost.of("http://nas.lan/"))
        assertThat(SiteHost.of("https://XN--CAF-DMA.FR/")).isEqualTo("xn--caf-dma.fr")
    }
}

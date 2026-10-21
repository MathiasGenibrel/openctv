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
}

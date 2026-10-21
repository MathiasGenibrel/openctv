package com.openctv.browser.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalNetworkHostTest {

    @Test
    fun `Given hosts of the local network and public sites When the warning is built Then local ones are recognised`() {
        listOf("localhost", "nas", "192.168.1.10", "10.0.0.5", "172.16.3.4", "box.local", "tv.lan", "[::1]", "[fd12::1]")
            .forEach { assertThat(isLocalNetworkHost(it)).isTrue() }
        listOf("expired.badssl.com", "8.8.8.8", "172.32.0.1", "example.org", null)
            .forEach { assertThat(isLocalNetworkHost(it)).isFalse() }
    }

    @Test
    fun `Given hosts with a private network extension When the warning is built Then the local network note applies`() {
        listOf("gitlab-dashboard.tools.internal", "a.local", "b.lan", "c.home", "d.home.arpa", "e.localdomain")
            .forEach { assertThat(isLocalNetworkHost(it)).isTrue() }
    }

    @Test
    fun `Given private network hosts When no scheme is typed Then they open in http`() {
        listOf("localhost", "192.168.1.10", "127.0.0.1", "gitlab-dashboard.tools.internal", "nas.local", "d.home.arpa")
            .forEach { assertThat(isHttpByDefaultHost(it)).isTrue() }
    }

    @Test
    fun `Given a public domain whose first label is a private word When no scheme is typed Then it stays https and is not local`() {
        listOf("internal.evil.com", "local.example.org", "home.arpa.example.com", "evilinternal.com", "example.org").forEach {
            assertThat(isHttpByDefaultHost(it)).isFalse()
            assertThat(isLocalNetworkHost(it)).isFalse()
        }
    }

    @Test
    fun `Given a name under a private extension When classified Then it is private because it only resolves on the local network`() {
        assertThat(isHttpByDefaultHost("evil.com.internal")).isTrue()
        assertThat(isLocalNetworkHost("evil.com.internal")).isTrue()
    }

    @Test
    fun `Given a host in capitals or with a trailing dot When classified Then it is read like its lowercase form`() {
        listOf("GitLab-Dashboard.Tools.INTERNAL", "gitlab-dashboard.tools.internal.", "LOCALHOST", "Nas.Local.").forEach {
            assertThat(isHttpByDefaultHost(it)).isTrue()
            assertThat(isLocalNetworkHost(it)).isTrue()
        }
    }

    @Test
    fun `Given a public ipv4 When classified Then it opens in http but gets no local network note`() {
        assertThat(isHttpByDefaultHost("8.8.8.8")).isTrue()
        assertThat(isLocalNetworkHost("8.8.8.8")).isFalse()
    }

    @Test
    fun `Given a single label or an ipv6 address When classified Then it is local but not opened in http by default`() {
        listOf("nas", "[::1]", "[fd12::1]").forEach {
            assertThat(isLocalNetworkHost(it)).isTrue()
            assertThat(isHttpByDefaultHost(it)).isFalse()
        }
    }
}

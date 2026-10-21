package com.openctv.browser.core.webview

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.security.SiteException
import org.junit.Test

class WebNavigationPolicyTest {

    @Test
    fun `Given a regular web link When the page navigates Then http and https are allowed`() {
        assertThat(WebNavigationPolicy.isUrlAllowed("https://www.arte.tv/fr/")).isTrue()
        assertThat(WebNavigationPolicy.isUrlAllowed("http://example.org")).isTrue()
        assertThat(WebNavigationPolicy.isUrlAllowed("  HTTPS://Example.org ")).isTrue()
    }

    @Test
    fun `Given a link to an external app or local resource When the user clicks it Then it is refused`() {
        val refused = listOf(
            "intent://scan/#Intent;scheme=zxing;end",
            "market://details?id=com.example",
            "file:///sdcard/secret.html",
            "javascript:alert(1)",
            "content://com.example/data",
            "data:text/html,<h1>x</h1>",
            "about:blank",
            "",
        )
        refused.forEach { url ->
            assertThat(WebNavigationPolicy.isUrlAllowed(url)).isFalse()
        }
        assertThat(WebNavigationPolicy.isUrlAllowed(null)).isFalse()
    }

    @Test
    fun `Given an invalid HTTPS certificate and no exception When WebView asks what to do Then the load never proceeds`() {
        val noException: (SiteException) -> Boolean = { false }

        assertThat(SslErrorPolicy.shouldProceed(true, "https://nas.maison/", "ab12", noException)).isFalse()
    }

    @Test
    fun `Given the user trusted this exact certificate for the host When the main page fails its check Then the load proceeds`() {
        val trusted = setOf(SiteException.Certificate("nas.maison", "ab12"))

        assertThat(SslErrorPolicy.shouldProceed(true, "https://NAS.maison:8443/x", "ab12", trusted::contains)).isTrue()
    }

    @Test
    fun `Given a trusted certificate When the host presents another certificate Then the load does not proceed`() {
        val trusted = setOf(SiteException.Certificate("nas.maison", "ab12"))

        assertThat(SslErrorPolicy.shouldProceed(true, "https://nas.maison/", "ff99", trusted::contains)).isFalse()
    }

    @Test
    fun `Given a trusted certificate for a host When another host or www of it fails Then the load does not proceed`() {
        val trusted = setOf(SiteException.Certificate("nas.maison", "ab12"))

        assertThat(SslErrorPolicy.shouldProceed(true, "https://www.nas.maison/", "ab12", trusted::contains)).isFalse()
        assertThat(SslErrorPolicy.shouldProceed(true, "https://evil.example/", "ab12", trusted::contains)).isFalse()
    }

    @Test
    fun `Given a trusted certificate When a subresource of the page fails Then it is never allowed to proceed`() {
        val trusted = setOf(SiteException.Certificate("nas.maison", "ab12"))

        assertThat(SslErrorPolicy.shouldProceed(false, "https://nas.maison/app.js", "ab12", trusted::contains)).isFalse()
    }

    @Test
    fun `Given a certificate that could not be read When an exception exists for the host Then the load does not proceed`() {
        val trusted = setOf(SiteException.Certificate("nas.maison", "ab12"))

        assertThat(SslErrorPolicy.shouldProceed(true, "https://nas.maison/", null, trusted::contains)).isFalse()
        assertThat(SslErrorPolicy.shouldProceed(true, null, "ab12", trusted::contains)).isFalse()
    }

    @Test
    fun `Given the SSL error codes of Android When the cause is shown Then each maps to a distinct reason`() {
        assertThat(certificateProblemOf(0)).isEqualTo(CertificateProblem.NotYetValid)
        assertThat(certificateProblemOf(1)).isEqualTo(CertificateProblem.Expired)
        assertThat(certificateProblemOf(2)).isEqualTo(CertificateProblem.NameMismatch)
        assertThat(certificateProblemOf(3)).isEqualTo(CertificateProblem.Untrusted)
        assertThat(certificateProblemOf(4)).isEqualTo(CertificateProblem.Invalid)
        assertThat(certificateProblemOf(5)).isEqualTo(CertificateProblem.Invalid)
    }

    @Test
    fun `Given a main page over http without exception When the page navigates Then a warning replaces it`() {
        val decision = WebNavigationPolicy.navigationOf("http://192.168.1.10/", isMainFrame = true) { false }

        assertThat(decision).isEqualTo(NavigationDecision.WarnHttp("192.168.1.10"))
    }

    @Test
    fun `Given an http host allowed by the user When the main page navigates Then it is allowed`() {
        val decision = WebNavigationPolicy.navigationOf("http://192.168.1.10:8080/a", isMainFrame = true) { it == "192.168.1.10" }

        assertThat(decision).isEqualTo(NavigationDecision.Allow)
    }

    @Test
    fun `Given an http host allowed by the user When a redirect leads to another http host Then the warning comes back`() {
        val decision = WebNavigationPolicy.navigationOf("http://other.example/", isMainFrame = true) { it == "192.168.1.10" }

        assertThat(decision).isEqualTo(NavigationDecision.WarnHttp("other.example"))
    }

    @Test
    fun `Given an https page or a subframe When it navigates Then no http warning applies`() {
        assertThat(WebNavigationPolicy.navigationOf("https://arte.tv/", isMainFrame = true) { false })
            .isEqualTo(NavigationDecision.Allow)
        assertThat(WebNavigationPolicy.navigationOf("http://ads.example/frame", isMainFrame = false) { false })
            .isEqualTo(NavigationDecision.Allow)
    }

    @Test
    fun `Given a non web address When it navigates Then it is refused even with exceptions`() {
        assertThat(WebNavigationPolicy.navigationOf("intent://x", isMainFrame = true) { true })
            .isEqualTo(NavigationDecision.Refuse)
    }

    @Test
    fun `Given an http address whose host cannot be read When it navigates Then it warns and nothing can allow it`() {
        val decision = WebNavigationPolicy.navigationOf("http://exa%6Dple.com/", isMainFrame = true) { true }

        assertThat(decision).isEqualTo(NavigationDecision.WarnHttp(null))
    }

    @Test
    fun `Given hosts of the local network and public sites When the warning is built Then local ones are recognised`() {
        listOf("localhost", "nas", "192.168.1.10", "10.0.0.5", "172.16.3.4", "box.local", "tv.lan", "[::1]", "[fd12::1]")
            .forEach { assertThat(isLocalNetworkHost(it)).isTrue() }
        listOf("expired.badssl.com", "8.8.8.8", "172.32.0.1", "example.org", null)
            .forEach { assertThat(isLocalNetworkHost(it)).isFalse() }
    }

    @Test
    fun `Given an SSL error on the page itself When it is reported Then it breaks the page but a subresource error does not`() {
        assertThat(SslErrorPolicy.isMainFrameError("https://bad.example/", "https://bad.example/")).isTrue()
        assertThat(SslErrorPolicy.isMainFrameError("https://cdn.bad/x.js", "https://good.example/")).isFalse()
    }

    @Test
    fun `Given a page requested without trailing slash When the SSL error reports it with one Then it still breaks the page`() {
        assertThat(SslErrorPolicy.isMainFrameError("https://expired.badssl.com/", "https://expired.badssl.com")).isTrue()
        assertThat(SslErrorPolicy.isMainFrameError("https://Expired.BadSSL.com/a/", "https://expired.badssl.com/a#x")).isTrue()
        assertThat(SslErrorPolicy.isMainFrameError("https://expired.badssl.com/", "https://other.org/")).isFalse()
        assertThat(SslErrorPolicy.isMainFrameError("https://expired.badssl.com/", null)).isFalse()
    }

    @Test
    fun `Given a main frame request before any page started When its certificate fails Then the error is for the page, not a subresource`() {
        val tracker = MainFrameTracker()

        tracker.onRequest("https://expired.badssl.com/", isMainFrame = true)
        tracker.onRequest("https://cdn.example/app.js", isMainFrame = false)

        assertThat(tracker.isMainFrameError("https://expired.badssl.com/")).isTrue()
        assertThat(tracker.isMainFrameError("https://cdn.example/app.js")).isFalse()
    }

    @Test
    fun `Given a redirect of the main frame When the redirect target has a bad certificate Then the error is for the page`() {
        val tracker = MainFrameTracker()

        tracker.onRequest("http://bad.example/", isMainFrame = true)
        tracker.onRequest("https://bad.example/login", isMainFrame = true)

        assertThat(tracker.isMainFrameError("https://bad.example/login")).isTrue()
    }

    @Test
    fun `Given a page asking for camera microphone or location When permissions are requested Then none is granted`() {
        val resources = listOf(
            "android.webkit.resource.VIDEO_CAPTURE",
            "android.webkit.resource.AUDIO_CAPTURE",
            "android.webkit.resource.PROTECTED_MEDIA_ID",
        )
        resources.forEach { assertThat(PagePermissionPolicy.shouldGrant(it)).isFalse() }
    }

    @Test
    fun `Given WebView error codes When the main page fails Then the cause tells offline from certificate problems`() {
        assertThat(loadErrorCauseOf(-2)).isEqualTo(LoadErrorCause.Unreachable)
        assertThat(loadErrorCauseOf(-6)).isEqualTo(LoadErrorCause.Unreachable)
        assertThat(loadErrorCauseOf(-8)).isEqualTo(LoadErrorCause.Unreachable)
        assertThat(loadErrorCauseOf(-11)).isEqualTo(LoadErrorCause.InvalidCertificate)
        assertThat(loadErrorCauseOf(-1)).isEqualTo(LoadErrorCause.Unknown)
    }
}

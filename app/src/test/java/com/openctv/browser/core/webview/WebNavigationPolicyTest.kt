package com.openctv.browser.core.webview

import com.google.common.truth.Truth.assertThat
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
    fun `Given an invalid HTTPS certificate When WebView asks what to do Then the load is never allowed to proceed`() {
        val errors = listOf(0, 1, 2, 3, 4, 5)
        errors.forEach { assertThat(SslErrorPolicy.shouldProceed(it)).isFalse() }
    }

    @Test
    fun `Given an SSL error on the page itself When it is reported Then it breaks the page but a subresource error does not`() {
        assertThat(SslErrorPolicy.isMainFrameError("https://bad.example/", "https://bad.example/")).isTrue()
        assertThat(SslErrorPolicy.isMainFrameError("https://cdn.bad/x.js", "https://good.example/")).isFalse()
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

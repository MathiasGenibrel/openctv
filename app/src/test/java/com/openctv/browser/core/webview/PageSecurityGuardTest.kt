package com.openctv.browser.core.webview

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.security.SecurityExceptionsContract
import com.openctv.browser.core.security.SiteException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Test

class PageSecurityGuardTest {

    private val exceptions = MutableExceptions()
    private val guard = PageSecurityGuard(exceptions)

    private val nasCertificate = SiteException.Certificate("nas.maison", "ab12")

    /** La page principale https://nas.maison/ a été acceptée malgré son certificat (proceed). */
    private fun givenNasPageAcceptedDespiteItsCertificate() {
        exceptions.allowed += nasCertificate
        guard.onMainFrameRequest("https://nas.maison/", isMainFrame = true)
        assertThat(guard.onSslError("https://nas.maison/", "ab12")).isTrue()
    }

    @Test
    fun `Given a trusted certificate When the main page fails its check Then it proceeds`() {
        givenNasPageAcceptedDespiteItsCertificate()
    }

    @Test
    fun `Given a trusted certificate When a subresource of the same host fails with it Then it never proceeds`() {
        exceptions.allowed += nasCertificate
        guard.onMainFrameRequest("https://nas.maison/", isMainFrame = true)
        guard.onMainFrameRequest("https://nas.maison/app.js", isMainFrame = false)

        assertThat(guard.isMainFrameError("https://nas.maison/app.js")).isFalse()
        assertThat(guard.onSslError("https://nas.maison/app.js", "ab12")).isFalse()
    }

    @Test
    fun `Given a newer main page was requested When the error of the previous one arrives Then it does not proceed`() {
        exceptions.allowed += nasCertificate
        guard.onMainFrameRequest("https://nas.maison/", isMainFrame = true)
        guard.onMainFrameRequest("https://arte.tv/", isMainFrame = true)

        assertThat(guard.onSslError("https://nas.maison/", "ab12")).isFalse()
    }

    @Test
    fun `Given an accepted certificate When the engine shows another page of the host without asking again Then the badge stays`() {
        givenNasPageAcceptedDespiteItsCertificate()

        val check = guard.checkShownPage("https://nas.maison/settings") { "ab12" }

        assertThat(check).isEqualTo(ShownPageCheck.UnverifiedCertificate)
    }

    @Test
    fun `Given an accepted certificate When the user removed the exception and the page shows again Then it is revoked once`() {
        givenNasPageAcceptedDespiteItsCertificate()
        exceptions.allowed -= nasCertificate

        assertThat(guard.checkShownPage("https://nas.maison/") { "ab12" }).isEqualTo(ShownPageCheck.CertificateRevoked)
        assertThat(guard.checkShownPage("https://nas.maison/") { "ab12" }).isEqualTo(ShownPageCheck.Verified)
    }

    @Test
    fun `Given an accepted certificate When the host now shows a certificate the engine validated Then the page is verified`() {
        givenNasPageAcceptedDespiteItsCertificate()

        assertThat(guard.checkShownPage("https://nas.maison/") { "ff99" }).isEqualTo(ShownPageCheck.Verified)
    }

    @Test
    fun `Given an accepted certificate When the shown certificate cannot be read Then the page keeps its unverified badge`() {
        givenNasPageAcceptedDespiteItsCertificate()

        assertThat(guard.checkShownPage("https://nas.maison/") { null }).isEqualTo(ShownPageCheck.UnverifiedCertificate)
    }

    @Test
    fun `Given no certificate was accepted When an https page shows Then its certificate is not even read`() {
        var isFingerprintRead = false

        val check = guard.checkShownPage("https://arte.tv/") { isFingerprintRead = true; "ab12" }

        assertThat(check).isEqualTo(ShownPageCheck.Verified)
        assertThat(isFingerprintRead).isFalse()
    }

    @Test
    fun `Given an accepted certificate for a host When www of it or another host shows the same certificate Then no badge applies`() {
        givenNasPageAcceptedDespiteItsCertificate()

        assertThat(guard.checkShownPage("https://www.nas.maison/") { "ab12" }).isEqualTo(ShownPageCheck.Verified)
        assertThat(guard.checkShownPage("http://nas.maison/") { "ab12" })
            .isEqualTo(ShownPageCheck.HttpNotAllowed(SecurityWarning.InsecureHttp("http://nas.maison/", "nas.maison")))
    }

    @Test
    fun `Given an http page reached without any decision When it shows without exception Then it is stopped behind the warning`() {
        val check = guard.checkShownPage("http://192.168.1.10/") { null }

        assertThat(check).isEqualTo(ShownPageCheck.HttpNotAllowed(SecurityWarning.InsecureHttp("http://192.168.1.10/", "192.168.1.10")))
    }

    @Test
    fun `Given an http exception removed When the page comes back from history Then the warning comes back`() {
        exceptions.allowed += SiteException.Http("192.168.1.10")
        assertThat(guard.checkShownPage("http://192.168.1.10/") { null }).isEqualTo(ShownPageCheck.Verified)

        exceptions.allowed -= SiteException.Http("192.168.1.10")

        assertThat(guard.checkShownPage("http://192.168.1.10/") { null }).isInstanceOf(ShownPageCheck.HttpNotAllowed::class.java)
    }

    @Test
    fun `Given about blank or a data page When it shows Then nothing is reported`() {
        assertThat(guard.checkShownPage("about:blank") { null }).isEqualTo(ShownPageCheck.Verified)
        assertThat(guard.checkShownPage("data:text/html,x") { null }).isEqualTo(ShownPageCheck.Verified)
    }

    private class MutableExceptions : SecurityExceptionsContract {
        val allowed = mutableSetOf<SiteException>()
        override val persisted: StateFlow<Resource<List<SiteException>>> = MutableStateFlow(Resource.Success(emptyList()))
        override fun isAllowed(exception: SiteException) = exception in allowed
        override suspend fun allow(exception: SiteException, isRemembered: Boolean): Resource<Unit> =
            Resource.Success(Unit).also { allowed += exception }
        override suspend fun remove(exception: SiteException): Resource<Unit> =
            Resource.Success(Unit).also { allowed -= exception }
    }
}

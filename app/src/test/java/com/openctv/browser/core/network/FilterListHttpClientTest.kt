package com.openctv.browser.core.network

import com.google.common.truth.Truth.assertThat
import java.net.InetAddress
import java.net.UnknownServiceException
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertThrows
import org.junit.Test

class FilterListHttpClientTest {

    // Aucun accès réseau : tout nom se résout en boucle locale, le refus doit tomber avant la connexion.
    private val client = filterListHttpClient(OkHttpClient.Builder().dns { listOf(InetAddress.getLoopbackAddress()) })

    @Test
    fun `Given the app allows cleartext for the WebView When a filter list is requested over http Then the client refuses it`() {
        val request = Request.Builder().url("http://easylist.to/easylist/easylist.txt").build()

        val error = assertThrows(UnknownServiceException::class.java) { client.newCall(request).execute() }

        assertThat(error).hasMessageThat().contains("CLEARTEXT")
    }

    @Test
    fun `Given the filter list client When its connection specs are read Then only modern TLS is allowed`() {
        assertThat(client.connectionSpecs).containsExactly(ConnectionSpec.MODERN_TLS)
    }
}

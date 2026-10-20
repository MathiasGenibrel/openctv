package com.openctv.browser.features.adblock.data

import android.webkit.WebResourceResponse
import com.openctv.browser.core.webview.RequestInterceptor
import com.openctv.browser.features.adblock.domain.AdBlockEngine
import com.openctv.browser.features.adblock.domain.BlockedRequestCounter
import com.openctv.browser.features.settings.domain.SettingsContract
import java.io.ByteArrayInputStream

/**
 * Branche le moteur sur `shouldInterceptRequest` (thread de fond, une fois par requête) : aucune I/O,
 * uniquement des lectures en mémoire. Réponse vide si la requête est bloquée, `null` sinon.
 */
class AdBlockInterceptor(
    private val engine: AdBlockEngine,
    private val settings: SettingsContract,
    private val counter: BlockedRequestCounter,
    private val emptyResponse: () -> WebResourceResponse = ::emptyWebResponse,
) : RequestInterceptor {

    override fun intercept(requestUrl: String, pageUrl: String?, isMainFrame: Boolean): WebResourceResponse? {
        // La page elle-même n'est jamais bloquée : l'utilisateur l'a demandée.
        if (isMainFrame) return null
        val isAdBlockEnabled = settings.current.isAdBlockEnabled
        if (!isAdBlockEnabled) return null
        val isBlocked = engine.shouldBlock(requestUrl, pageUrl)
        if (!isBlocked) return null

        if (pageUrl != null) counter.increment(pageUrl)
        return emptyResponse()
    }
}

private fun emptyWebResponse() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

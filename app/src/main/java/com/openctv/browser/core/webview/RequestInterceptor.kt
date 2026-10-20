package com.openctv.browser.core.webview

import android.webkit.WebResourceResponse

/**
 * Point d'extension de `shouldInterceptRequest` (thread de fond, une fois par requête) : décision
 * synchrone, sans I/O. `null` laisse passer la requête ; une réponse vide la bloque.
 * Le bloqueur de pubs s'y branchera ; [PassThrough] par défaut.
 */
fun interface RequestInterceptor {
    /** [pageUrl] est l'URL de la page courante, mémorisée côté thread principal. */
    fun intercept(requestUrl: String, pageUrl: String?, isMainFrame: Boolean): WebResourceResponse?

    companion object {
        val PassThrough = RequestInterceptor { _, _, _ -> null }
    }
}

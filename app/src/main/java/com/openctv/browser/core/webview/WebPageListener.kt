package com.openctv.browser.core.webview

/** Événements de chargement remontés par la WebView, tous sur le thread principal. */
interface WebPageListener {
    fun onPageStarted(url: String)
    fun onProgressChanged(progress: Int)
    fun onPageFinished(url: String)
    fun onLoadError(error: WebLoadError)

    /**
     * L'adresse affichée a changé, y compris sans rechargement (navigation interne d'une application
     * monopage comme YouTube : `history.pushState`), avec l'état de l'historique de la WebView à cet instant.
     */
    fun onUrlChanged(url: String, canGoBack: Boolean, canGoForward: Boolean)
}

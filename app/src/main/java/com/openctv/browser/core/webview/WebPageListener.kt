package com.openctv.browser.core.webview

/** Événements de chargement remontés par la WebView, tous sur le thread principal. */
interface WebPageListener {
    fun onPageStarted(url: String)
    fun onProgressChanged(progress: Int)
    fun onPageFinished(url: String)
    fun onLoadError(error: WebLoadError)
}

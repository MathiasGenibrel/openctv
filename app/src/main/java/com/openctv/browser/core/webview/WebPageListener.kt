package com.openctv.browser.core.webview

/** Événements de chargement remontés par la WebView, tous sur le thread principal. */
interface WebPageListener {
    fun onPageStarted(url: String)
    fun onProgressChanged(progress: Int)
    fun onPageFinished(url: String)

    /**
     * La page principale [url] est déjà visible (premier rendu, avant la fin du chargement de ses
     * sous-ressources) : le voile de chargement peut disparaître, la barre de progression continue.
     */
    fun onPageCommitVisible(url: String)
    fun onLoadError(error: WebLoadError)

    /**
     * La page principale [url] s'affiche malgré un certificat invalide, parce que l'utilisateur a accordé une
     * exception pour cet hôte et ce certificat : la barre d'outils doit le dire.
     */
    fun onUnverifiedCertificateAccepted(url: String)

    /**
     * L'adresse affichée a changé, y compris sans rechargement (navigation interne d'une application
     * monopage comme YouTube : `history.pushState`), avec l'état de l'historique de la WebView à cet instant.
     */
    fun onUrlChanged(url: String, canGoBack: Boolean, canGoForward: Boolean)
}

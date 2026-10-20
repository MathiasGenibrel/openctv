package com.openctv.browser.core.webview

import android.view.View
import android.webkit.WebChromeClient

/**
 * Côté vue du plein écran vidéo : détient la [View] du lecteur et le [WebChromeClient.CustomViewCallback]
 * le temps du plein écran. Les deux méthodes sont appelées sur le thread principal.
 */
interface FullscreenHost {
    /** La page demande le plein écran : afficher [view] au-dessus de tout. [callback] la rend à la page. */
    fun onShow(view: View, callback: WebChromeClient.CustomViewCallback)

    /** La page quitte le plein écran : retirer la vue et la libérer. */
    fun onHide()
}

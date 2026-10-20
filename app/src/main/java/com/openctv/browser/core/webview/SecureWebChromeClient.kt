package com.openctv.browser.core.webview

import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView

/** Client de base : progression remontée, permissions et géolocalisation des pages refusées. */
class SecureWebChromeClient(private val listener: WebPageListener) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        listener.onProgressChanged(newProgress)
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        val granted = request.resources.filter(PagePermissionPolicy::shouldGrant)
        if (granted.isEmpty()) request.deny() else request.grant(granted.toTypedArray())
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        callback.invoke(origin, false, false)
    }
}

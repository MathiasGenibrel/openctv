package com.openctv.browser.core.webview

import org.koin.dsl.module

/**
 * Fabrique unique de la WebView verrouillée. Le [RequestInterceptor] branché sur
 * `shouldInterceptRequest` est fourni par une feature (le bloqueur de pubs).
 */
val webViewModule = module {
    single { LockedWebViewFactory(requestInterceptor = get()) }
}

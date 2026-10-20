package com.openctv.browser.features.browser.ui

import androidx.lifecycle.ViewModel
import com.openctv.browser.core.webview.LoadErrorCause
import com.openctv.browser.core.webview.WebLoadError
import com.openctv.browser.core.webview.WebNavigationPolicy
import com.openctv.browser.core.webview.WebPageListener
import com.openctv.browser.features.browser.domain.PageState
import com.openctv.browser.features.browser.domain.siteLabelOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** Ordre adressé à la WebView (la vue exécute, le ViewModel décide). */
sealed interface BrowserCommand {
    data class Load(val url: String) : BrowserCommand
}

class BrowserViewModel(initialUrl: String) : ViewModel(), WebPageListener {
    private var currentUrl: String = initialUrl

    private val _pageState = MutableStateFlow<PageState>(initialState(initialUrl))
    val pageState: StateFlow<PageState> = _pageState.asStateFlow()

    private val _commands = Channel<BrowserCommand>(Channel.BUFFERED)
    val commands: Flow<BrowserCommand> = _commands.receiveAsFlow()

    init {
        val isInitialUrlAllowed = WebNavigationPolicy.isUrlAllowed(initialUrl)
        if (isInitialUrlAllowed) _commands.trySend(BrowserCommand.Load(initialUrl))
    }

    override fun onPageStarted(url: String) {
        currentUrl = url
        _pageState.value = PageState.Loading(siteLabelOf(url), progress = 0f)
    }

    override fun onProgressChanged(progress: Int) {
        val state = _pageState.value
        if (state is PageState.Loading) {
            _pageState.value = state.copy(progress = progress.coerceIn(0, 100) / 100f)
        }
    }

    override fun onPageFinished(url: String) {
        val state = _pageState.value
        // Après une erreur, WebView termine quand même « la page » (sa page d'erreur interne).
        val isShowingError = state is PageState.Error
        if (!isShowingError) _pageState.value = PageState.Loaded(siteLabelOf(url))
    }

    override fun onLoadError(error: WebLoadError) {
        val failedUrl = error.url ?: currentUrl
        _pageState.value = PageState.Error(siteLabelOf(failedUrl), error.cause)
    }

    fun retry() {
        _pageState.value = PageState.Loading(siteLabelOf(currentUrl), progress = 0f)
        _commands.trySend(BrowserCommand.Load(currentUrl))
    }

    private fun initialState(url: String): PageState {
        val site = siteLabelOf(url)
        val isAllowed = WebNavigationPolicy.isUrlAllowed(url)
        return if (isAllowed) PageState.Loading(site, progress = 0f) else PageState.Error(site, LoadErrorCause.Unknown)
    }
}

package com.openctv.browser.features.browser.ui

import androidx.lifecycle.ViewModel
import com.openctv.browser.core.webview.LoadErrorCause
import com.openctv.browser.core.webview.WebLoadError
import com.openctv.browser.core.webview.WebNavigationPolicy
import com.openctv.browser.core.webview.WebPageListener
import com.openctv.browser.features.browser.domain.PageState
import com.openctv.browser.features.browser.domain.siteLabelOf
import com.openctv.browser.features.search.domain.SearchMode
import com.openctv.browser.features.search.domain.SearchRequest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** Ordre adressé à la vue, qui l'exécute sur sa WebView (la vue exécute, le ViewModel décide). */
sealed interface BrowserCommand {
    data class Load(val url: String) : BrowserCommand
    data object GoBack : BrowserCommand

    /** Quitter l'écran de navigation (retour à l'accueil). */
    data object Close : BrowserCommand
}

class BrowserViewModel(
    initialUrl: String,
    /** Appelé à chaque page chargée avec succès (alimente « Reprendre la dernière page »). */
    private val onPageVisited: (String) -> Unit = {},
) : ViewModel(), WebPageListener {
    private var currentUrl: String = initialUrl

    private val _pageState = MutableStateFlow(freshStateFor(initialUrl))
    val pageState: StateFlow<PageState> = _pageState.asStateFlow()

    private val _commands = Channel<BrowserCommand>(Channel.BUFFERED)
    val commands: Flow<BrowserCommand> = _commands.receiveAsFlow()

    private val _addressInput = MutableStateFlow<SearchRequest?>(null)

    /**
     * Saisie d'adresse ouverte en surcouche de la page (`null` = fermée). La page, sa WebView et son
     * historique restent vivants dessous : fermer la saisie ne recharge rien.
     */
    val addressInput: StateFlow<SearchRequest?> = _addressInput.asStateFlow()

    /**
     * Une WebView vierge vient d'être créée (ouverture de l'écran, ou recréation de l'activité alors
     * que ce ViewModel survit) : elle charge la page courante.
     */
    fun onWebViewReady() = load(currentUrl)

    fun retry() = load(currentUrl)

    /** Retour : fermer la saisie ouverte, sinon reculer dans l'historique de la page, sinon revenir à l'accueil. */
    fun onBack(canGoBack: Boolean) {
        val isAddressInputOpen = _addressInput.value != null
        if (isAddressInputOpen) {
            closeAddressInput()
            return
        }
        val command = if (canGoBack) BrowserCommand.GoBack else BrowserCommand.Close
        _commands.trySend(command)
    }

    /** Ouvre la saisie par-dessus la page, pré-remplie avec son adresse (OK sur le champ d'adresse, #7). */
    fun openAddressInput() {
        _addressInput.value = SearchRequest(SearchMode.Browse, initialText = currentUrl)
    }

    /** Ferme la saisie sans rien charger : la page est intacte, l'écran rend le focus (cf. BrowserScreen). */
    fun closeAddressInput() {
        _addressInput.value = null
    }

    /** Adresse ou recherche validée dans la saisie : chargée dans la même WebView (historique conservé). */
    fun onAddressChosen(url: String) {
        closeAddressInput()
        load(url)
    }

    override fun onPageStarted(url: String) {
        currentUrl = url
        val isPageShown = when (val state = _pageState.value) {
            is PageState.Loaded -> true
            is PageState.Loading -> state.isPageShown
            is PageState.Error -> false
        }
        _pageState.value = PageState.Loading(siteLabelOf(url), progress = 0f, isPageShown = isPageShown)
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
        if (!isShowingError) {
            _pageState.value = PageState.Loaded(siteLabelOf(url))
            onPageVisited(url)
        }
    }

    override fun onLoadError(error: WebLoadError) {
        val failedUrl = error.url ?: currentUrl
        _pageState.value = PageState.Error(siteLabelOf(failedUrl), error.cause)
    }

    /** Seul chemin vers `loadUrl` : `loadUrl` contourne `shouldOverrideUrlLoading`, la politique est appliquée ici. */
    private fun load(url: String) {
        val state = freshStateFor(url)
        _pageState.value = state
        val isAllowed = state is PageState.Loading
        if (isAllowed) _commands.trySend(BrowserCommand.Load(url))
    }

    private fun freshStateFor(url: String): PageState {
        val site = siteLabelOf(url)
        val isAllowed = WebNavigationPolicy.isUrlAllowed(url)
        return if (isAllowed) PageState.Loading(site, progress = 0f) else PageState.Error(site, LoadErrorCause.Unknown)
    }
}

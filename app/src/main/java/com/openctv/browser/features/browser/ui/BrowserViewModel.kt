package com.openctv.browser.features.browser.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.webview.LoadErrorCause
import com.openctv.browser.core.webview.WebLoadError
import com.openctv.browser.core.webview.WebNavigationPolicy
import com.openctv.browser.core.webview.WebPageListener
import com.openctv.browser.features.bookmarks.domain.BookmarksContract
import com.openctv.browser.features.browser.domain.BackAction
import com.openctv.browser.features.browser.domain.BrowserHud
import com.openctv.browser.features.browser.domain.BrowserLayers
import com.openctv.browser.features.browser.domain.BrowserMode
import com.openctv.browser.features.browser.domain.BrowserNotice
import com.openctv.browser.features.browser.domain.PageState
import com.openctv.browser.features.browser.domain.ToolbarState
import com.openctv.browser.features.browser.domain.MenuWriteFailure
import com.openctv.browser.features.browser.domain.backAction
import com.openctv.browser.features.browser.domain.browserHudOf
import com.openctv.browser.features.browser.domain.siteLabelOf
import com.openctv.browser.features.browser.domain.toolbarStateOf
import com.openctv.browser.features.search.domain.SearchMode
import com.openctv.browser.features.search.domain.SearchRequest
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ordre adressé à la vue, qui l'exécute sur sa WebView (la vue exécute, le ViewModel décide). */
sealed interface BrowserCommand {
    data class Load(val url: String) : BrowserCommand
    data object GoBack : BrowserCommand
    data object GoForward : BrowserCommand
    data object Reload : BrowserCommand
    data object Stop : BrowserCommand

    /** Demander au lecteur de quitter le plein écran (la vue appelle `onCustomViewHidden`). */
    data object ExitFullscreen : BrowserCommand

    /** Quitter l'écran de navigation (retour à l'accueil). */
    data object Close : BrowserCommand
}

class BrowserViewModel(
    initialUrl: String,
    private val bookmarks: BookmarksContract,
    /** Appelé à chaque page chargée avec succès (alimente « Reprendre la dernière page »). */
    private val onPageVisited: (String) -> Unit = {},
) : ViewModel(), WebPageListener {
    private val _pageUrl = MutableStateFlow(initialUrl)

    /** Adresse de la page affichée, suivie aussi dans les applications monopage (cf. [onUrlChanged]). */
    val pageUrl: StateFlow<String> = _pageUrl.asStateFlow()

    private val _canGoBack = MutableStateFlow(false)
    private val _canGoForward = MutableStateFlow(false)

    private val _pageState = MutableStateFlow(freshStateFor(initialUrl))
    val pageState: StateFlow<PageState> = _pageState.asStateFlow()

    private val _commands = Channel<BrowserCommand>(Channel.BUFFERED)
    val commands: Flow<BrowserCommand> = _commands.receiveAsFlow()

    private val _layers = MutableStateFlow(BrowserLayers())

    /**
     * Couches au-dessus de la page (saisie, menu, mode interface) : qui reçoit le D-pad et Retour. La page,
     * sa WebView et son historique restent vivants dessous : fermer une couche ne recharge rien.
     */
    val layers: StateFlow<BrowserLayers> = _layers.asStateFlow()

    /** Barre d'outils et aide affichées par-dessus la page. */
    val hud: StateFlow<BrowserHud> = combine(_layers, _pageState, ::browserHudOf).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        browserHudOf(_layers.value, _pageState.value),
    )

    private val _notice = MutableStateFlow<BrowserNotice?>(null)
    private var noticeJob: Job? = null

    /** Message bref (échec d'un favori…), effacé seul après [NOTICE_DURATION]. `null` = aucun. */
    val notice: StateFlow<BrowserNotice?> = _notice.asStateFlow()

    private val isBookmarked: StateFlow<Boolean> = _pageUrl
        .flatMapLatest { bookmarks.isBookmarked(it) }
        .map { resource -> resource is Resource.Success && resource.data }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** Contenu de la barre d'outils (boutons actifs, Actualiser/Arrêter, adresse, favori). */
    val toolbar: StateFlow<ToolbarState> = combine(
        _pageState, _pageUrl, _canGoBack, _canGoForward, isBookmarked, ::toolbarStateOf,
    ).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        toolbarStateOf(_pageState.value, initialUrl, false, false, false),
    )

    /**
     * Une WebView vierge vient d'être créée (ouverture de l'écran, ou recréation de l'activité alors
     * que ce ViewModel survit) : elle charge la page courante.
     */
    fun onWebViewReady() = load(_pageUrl.value)

    fun retry() = load(_pageUrl.value)

    /** Retour retire la couche la plus haute, puis recule dans l'historique, puis revient à l'accueil ([backAction]). */
    fun onBack(canGoBack: Boolean) {
        when (_layers.value.backAction(canGoBack)) {
            BackAction.ExitFullscreen -> _commands.trySend(BrowserCommand.ExitFullscreen)
            BackAction.CloseAddressInput -> closeAddressInput()
            BackAction.CloseMenu -> closeMenu()
            BackAction.ExitInterface -> exitInterfaceMode()
            BackAction.GoBackInHistory -> _commands.trySend(BrowserCommand.GoBack)
            BackAction.LeaveBrowser -> _commands.trySend(BrowserCommand.Close)
        }
    }

    /** Le D-pad pilote désormais la barre d'outils (aussi appelé par « maintenir Haut », #8). */
    fun enterInterfaceMode() {
        _layers.update { it.copy(mode = BrowserMode.Interface) }
    }

    /** Le D-pad pilote de nouveau la page. */
    fun exitInterfaceMode() {
        _layers.update { it.copy(mode = BrowserMode.Page) }
    }

    /**
     * Flèche Haut dans la page. Si elle ne peut plus défiler vers le haut ([canScrollUp] faux), elle
     * ouvre le mode interface. Renvoie `true` si l'événement est consommé (sinon la page le reçoit).
     */
    fun onDpadUp(canScrollUp: Boolean): Boolean {
        val layers = _layers.value
        val isPageMode = !layers.isInterfaceMode
        val isAtTopOfPage = !canScrollUp
        val shouldEnterInterface = isPageMode && isAtTopOfPage && !layers.isOverlayOpen
        if (shouldEnterInterface) enterInterfaceMode()
        return shouldEnterInterface
    }

    /** Flèche Bas dans la barre d'outils : retour à la page. Renvoie `true` si consommé. */
    fun onDpadDown(): Boolean {
        val layers = _layers.value
        val shouldReturnToPage = layers.isInterfaceMode && !layers.isOverlayOpen
        if (shouldReturnToPage) exitInterfaceMode()
        return shouldReturnToPage
    }

    /** Touche Menu de la télécommande : ouvre le mode interface. Renvoie `true` si consommé. */
    fun onMenuKey(): Boolean {
        if (_layers.value.isOverlayOpen) return false
        enterInterfaceMode()
        return true
    }

    /** La page est passée en plein écran vidéo (la vue détient la View du lecteur). */
    fun onFullscreenShown() {
        _layers.update { it.copy(isFullscreen = true) }
    }

    /** Le plein écran est terminé (sortie demandée par Retour ou par la page elle-même). */
    fun onFullscreenHidden() {
        _layers.update { it.copy(isFullscreen = false) }
    }

    /** Bouton Menu de la barre : sans effet sous une autre surcouche (saisie, plein écran vidéo). */
    fun openMenu() {
        if (_layers.value.isOverlayOpen) return
        _layers.update { it.copy(isMenuOpen = true) }
    }

    fun closeMenu() {
        _layers.update { it.copy(isMenuOpen = false) }
    }

    fun goBack() {
        if (_canGoBack.value) _commands.trySend(BrowserCommand.GoBack)
    }

    fun goForward() {
        if (_canGoForward.value) _commands.trySend(BrowserCommand.GoForward)
    }

    /**
     * Actualiser ou, pendant le chargement, Arrêter. Un rechargement laisse `onPageStarted` décider de
     * l'affichage (la page reste visible sous la barre de progression) ; après une erreur, on recharge
     * l'adresse via [retry] puisque la WebView n'affiche pas de vraie page.
     */
    fun onReloadOrStop() {
        when (val state = _pageState.value) {
            is PageState.Loading -> {
                _commands.trySend(BrowserCommand.Stop)
                // WebView n'appelle pas onPageFinished après stopLoading : on clôt le chargement ici.
                _pageState.value = PageState.Loaded(state.site)
            }
            is PageState.Error -> retry()
            is PageState.Loaded -> _commands.trySend(BrowserCommand.Reload)
        }
    }

    /** « Actualiser la page » du menu : recharge la page (ou réessaie après une erreur) et ferme le menu. */
    fun reloadFromMenu() {
        closeMenu()
        when (_pageState.value) {
            is PageState.Error -> retry()
            is PageState.Loading, is PageState.Loaded -> _commands.trySend(BrowserCommand.Reload)
        }
    }

    /** « Accueil » du menu : quitte le navigateur (la WebView et son historique sont libérés avec l'écran). */
    fun goHome() {
        _commands.trySend(BrowserCommand.Close)
    }

    /**
     * Favori : retire la page courante si elle l'est déjà, sinon l'ajoute (titre = domaine). Un échec du
     * stockage est signalé par [BrowserNotice.BookmarkFailed] ; l'étoile reste sur l'état réel.
     */
    fun toggleBookmark() {
        val url = _pageUrl.value
        viewModelScope.launch {
            val current = bookmarks.isBookmarked(url).first { it !is Resource.Loading }
            val isAlreadyBookmarked = current is Resource.Success && current.data
            val result = if (isAlreadyBookmarked) bookmarks.remove(url) else bookmarks.add(url, siteLabelOf(url))
            if (result is Resource.Error) showNotice(BrowserNotice.BookmarkFailed)
        }
    }

    /** Le menu n'a pas pu enregistrer un choix : un message bref l'explique, sans prendre le focus. */
    fun onMenuWriteFailed(failure: MenuWriteFailure) {
        val notice = when (failure) {
            MenuWriteFailure.AdBlock -> BrowserNotice.AdBlockFailed
            MenuWriteFailure.Bookmark -> BrowserNotice.BookmarkFailed
            MenuWriteFailure.Setting -> BrowserNotice.SettingFailed
        }
        showNotice(notice)
    }

    private fun showNotice(notice: BrowserNotice) {
        noticeJob?.cancel()
        noticeJob = viewModelScope.launch {
            _notice.value = notice
            delay(NOTICE_DURATION)
            _notice.value = null
        }
    }

    /** Ouvre la saisie par-dessus la page, pré-remplie avec son adresse (OK sur le champ d'adresse, #7). */
    fun openAddressInput() {
        val request = SearchRequest(SearchMode.Browse, initialText = _pageUrl.value)
        _layers.update { it.copy(addressInput = request) }
    }

    /** Ferme la saisie sans rien charger : la page est intacte, l'écran rend le focus (cf. BrowserFocus). */
    fun closeAddressInput() {
        _layers.update { it.copy(addressInput = null) }
    }

    /** Adresse ou recherche validée dans la saisie : chargée dans la même WebView (historique conservé). */
    fun onAddressChosen(url: String) {
        _layers.update { it.copy(addressInput = null, mode = BrowserMode.Page) }
        load(url)
    }

    override fun onPageStarted(url: String) {
        _pageUrl.value = url
        val isPageShown = when (val state = _pageState.value) {
            is PageState.Loaded -> true
            is PageState.Loading -> state.isPageShown
            is PageState.Error -> false
        }
        _pageState.value = PageState.Loading(siteLabelOf(url), progress = 0f, isPageShown = isPageShown)
    }

    override fun onUrlChanged(url: String, canGoBack: Boolean, canGoForward: Boolean) {
        _pageUrl.value = url
        _canGoBack.value = canGoBack
        _canGoForward.value = canGoForward
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
        val failedUrl = error.url ?: _pageUrl.value
        _pageState.value = PageState.Error(siteLabelOf(failedUrl), error.cause)
    }

    /** Seul chemin vers `loadUrl` : `loadUrl` contourne `shouldOverrideUrlLoading`, la politique est appliquée ici. */
    private fun load(url: String) {
        _pageUrl.value = url
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

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        val NOTICE_DURATION = 4.seconds
    }
}

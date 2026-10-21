package com.openctv.browser.features.browser.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.history.NoPageVisits
import com.openctv.browser.core.history.PageVisitsContract
import com.openctv.browser.core.security.NoSecurityExceptions
import com.openctv.browser.core.security.SecurityExceptionsContract
import com.openctv.browser.core.security.SiteHost
import com.openctv.browser.core.webview.LoadErrorCause
import com.openctv.browser.core.webview.NavigationDecision
import com.openctv.browser.core.webview.WebLoadError
import com.openctv.browser.core.webview.WebNavigationPolicy
import com.openctv.browser.core.webview.WebPageListener
import com.openctv.browser.core.zoom.NoPageZoom
import com.openctv.browser.core.zoom.PageZoom
import com.openctv.browser.core.zoom.PageZoomContract
import com.openctv.browser.core.zoom.effectiveZoom
import com.openctv.browser.features.bookmarks.domain.BookmarksContract
import com.openctv.browser.features.browser.domain.BackAction
import com.openctv.browser.features.browser.domain.BrowserHud
import com.openctv.browser.features.browser.domain.BrowserLayers
import com.openctv.browser.features.browser.domain.BrowserMode
import com.openctv.browser.features.browser.domain.BrowserNotice
import com.openctv.browser.features.browser.domain.HudAutoHide
import com.openctv.browser.features.browser.domain.PageState
import com.openctv.browser.features.browser.domain.PageUnderError
import com.openctv.browser.features.browser.domain.RemoteKey
import com.openctv.browser.features.browser.domain.RevealKeyGuard
import com.openctv.browser.features.browser.domain.ToolbarState
import com.openctv.browser.features.browser.domain.MenuWriteFailure
import com.openctv.browser.features.browser.domain.backAction
import com.openctv.browser.features.browser.domain.browserHudOf
import com.openctv.browser.features.browser.domain.siteLabelOf
import com.openctv.browser.features.browser.domain.toolbarStateOf
import com.openctv.browser.features.search.domain.SearchMode
import com.openctv.browser.features.search.domain.SearchRequest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
    /** Délai d'inactivité avant masquage de l'interface, lu à chaque activité (`null` = jamais). */
    hudHideDelay: () -> Duration? = { null },
    /** Exceptions de sécurité accordées (HTTP, certificat) : décident d'avertir ou d'afficher la page. */
    private val securityExceptions: SecurityExceptionsContract = NoSecurityExceptions,
    /** Sites avec leur propre zoom ; les autres suivent [defaultZoom]. */
    private val siteZooms: PageZoomContract = NoPageZoom,
    /** Zoom des Réglages pour les sites sans zoom propre. */
    private val defaultZoom: StateFlow<PageZoom> = MutableStateFlow(PageZoom.Default),
    /** Historique : reçoit chaque page principale réellement affichée (jamais un avertissement ni une erreur). */
    private val pageVisits: PageVisitsContract = NoPageVisits,
) : ViewModel(), WebPageListener {
    private val _pageUrl = MutableStateFlow(initialUrl)

    /** Adresse de la page affichée, suivie aussi dans les applications monopage (cf. [onUrlChanged]). */
    val pageUrl: StateFlow<String> = _pageUrl.asStateFlow()

    /**
     * Zoom de la page affichée : celui de son hôte exact, sinon celui des Réglages. Suit la page (redirection,
     * lien vers un autre site) ; la vue l'applique à sa WebView sans la recharger.
     */
    val pageZoom: StateFlow<PageZoom> = combine(_pageUrl, siteZooms.sites, defaultZoom) { url, sites, default ->
        effectiveZoom(url, (sites as? Resource.Success)?.data.orEmpty(), default)
    }.distinctUntilChanged().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        effectiveZoom(initialUrl, (siteZooms.sites.value as? Resource.Success)?.data.orEmpty(), defaultZoom.value),
    )

    private val _canGoBack = MutableStateFlow(false)
    private val _canGoForward = MutableStateFlow(false)

    /** Hôte dont la page principale est affichée malgré un certificat invalide (exception accordée), sinon `null`. */
    private val _unverifiedCertificateHost = MutableStateFlow<String?>(null)

    /** Dernier hôte pour lequel un message « page non sécurisée » a été montré : un seul message par site visité. */
    private var announcedInsecureHost: String? = null

    /** Dernière adresse entrée dans l'historique de la WebView ([onUrlChanged]) : la page qu'elle affiche vraiment. */
    private var committedUrl: String? = null

    /** Titre de la page en cours de chargement ou affichée ; vide tant que la page n'en a pas donné. */
    private var pageTitle: String = ""

    /** Page affichée déjà enregistrée dans l'historique : un titre tardif la complète. `null` = rien d'enregistré. */
    private var recordedUrl: String? = null

    /** Adresse de la page en échec affichée par l'écran d'erreur (significative en [PageState.Error] seulement). */
    private var failedUrl: String = initialUrl

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

    private val _isCursorHelpRequested = MutableStateFlow(true)
    private var cursorHelpJob: Job? = null

    private val _notice = MutableStateFlow<BrowserNotice?>(null)
    private var noticeJob: Job? = null

    /** Message bref (échec d'un favori…), effacé seul après [NOTICE_DURATION]. `null` = aucun. */
    val notice: StateFlow<BrowserNotice?> = _notice.asStateFlow()

    private val autoHide = HudAutoHide(viewModelScope, hudHideDelay)
    private val revealKeys = RevealKeyGuard()

    /** Barre d'outils, aide et pointeur affichés par-dessus la page (masqués à l'inactivité, cf. [HudAutoHide]). */
    val hud: StateFlow<BrowserHud> = combine(
        _layers, _pageState, _isCursorHelpRequested, autoHide.isIdle, _notice,
    ) { layers, pageState, isCursorHelpRequested, isIdle, notice ->
        browserHudOf(layers, pageState, isCursorHelpRequested, isIdle, isNoticeShown = notice != null)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        currentHud(),
    )

    init {
        showCursorHelp()
        // Un changement de couche, d'état de la page (menu fermé, chargement fini…) ou de message bref (affiché,
        // effacé) compte comme une activité : l'interface ne se masque jamais juste après, le délai repart de zéro.
        viewModelScope.launch {
            val pageStateKind = _pageState.map { it::class }.distinctUntilChanged()
            val isNoticeShown = _notice.map { it != null }.distinctUntilChanged()
            combine(_layers, pageStateKind, isNoticeShown) { _, _, _ -> }.collect { autoHide.onUserActivity() }
        }
    }

    private fun currentHud() = browserHudOf(
        _layers.value, _pageState.value, _isCursorHelpRequested.value, autoHide.isIdle.value,
        isNoticeShown = _notice.value != null,
    )

    /**
     * Une touche de la télécommande vient d'arriver (appui, répétition ou relâchement) : elle relance le minuteur.
     * Renvoie `true` si elle ne doit rien faire d'autre que réafficher l'interface masquée : la touche qui
     * réaffiche est avalée jusqu'à son relâchement ([RevealKeyGuard]), rien n'est transmis à la page, au curseur
     * ni à la barre. Retour garde son rôle et réaffiche aussi.
     */
    fun onRemoteKey(key: RemoteKey): Boolean {
        val wasHidden = currentHud().isHidden
        autoHide.onUserActivity()
        return revealKeys.swallows(key, isHudHidden = wasHidden)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val isBookmarked: StateFlow<Boolean> = _pageUrl
        .flatMapLatest { bookmarks.isBookmarked(it) }
        .map { resource -> resource is Resource.Success && resource.data }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** Contenu de la barre d'outils (boutons actifs, Actualiser/Arrêter, adresse, favori). */
    val toolbar: StateFlow<ToolbarState> = combine(
        combine(_pageState, _pageUrl, _unverifiedCertificateHost, ::Triple),
        combine(_canGoBack, _canGoForward, isBookmarked, ::Triple),
    ) { (pageState, pageUrl, unverifiedHost), (canGoBack, canGoForward, isBookmarked) ->
        toolbarStateOf(pageState, pageUrl, canGoBack, canGoForward, isBookmarked, unverifiedHost)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        toolbarStateOf(_pageState.value, initialUrl, false, false, false),
    )

    /**
     * Une WebView vierge vient d'être créée (ouverture de l'écran, ou recréation de l'activité alors
     * que ce ViewModel survit) : elle charge la page courante.
     */
    fun onWebViewReady() {
        committedUrl = null
        load(_pageUrl.value)
    }

    fun retry() = load(_pageUrl.value)

    /**
     * Retour retire la couche la plus haute, ré-affiche la page restée sous un écran d'erreur, recule dans
     * l'historique, puis revient à l'accueil : la règle est dans [backAction], le ViewModel n'en fournit que les faits.
     */
    fun onBack(canGoBack: Boolean) {
        autoHide.onUserActivity()
        val shownPageUrl = committedUrl
        when (_layers.value.backAction(canGoBack, pageUnderError(shownPageUrl))) {
            BackAction.DismissError -> shownPageUrl?.let(::showPreviousPage)
            BackAction.ExitFullscreen -> _commands.trySend(BrowserCommand.ExitFullscreen)
            BackAction.CloseHistory -> closeHistory()
            BackAction.CloseAddressInput -> closeAddressInput()
            BackAction.CloseMenu -> closeMenu()
            BackAction.ExitInterface -> exitInterfaceMode()
            BackAction.CollapseWarningOptions -> setWarningOptionsOpen(false)
            BackAction.GoBackInHistory -> _commands.trySend(BrowserCommand.GoBack)
            BackAction.LeaveBrowser -> _commands.trySend(BrowserCommand.Close)
        }
    }

    /**
     * Ce que l'écran d'erreur recouvre : la dernière page entrée dans l'historique ([shownPageUrl]) n'est pas celle
     * en échec (ex. certificat refusé, schéma interdit), la WebView l'affiche donc toujours dessous ([backAction]).
     */
    private fun pageUnderError(shownPageUrl: String?): PageUnderError {
        val error = _pageState.value as? PageState.Error ?: return PageUnderError.None
        val isPreviousPageBehind = shownPageUrl != null && shownPageUrl != failedUrl
        return when {
            !isPreviousPageBehind -> PageUnderError.None
            error.warning != null -> PageUnderError.BehindSecurityWarning
            else -> PageUnderError.BehindLoadError
        }
    }

    /** L'écran d'erreur se retire : la page que la WebView n'a jamais quittée redevient visible, sans rechargement. */
    private fun showPreviousPage(url: String) {
        setWarningOptionsOpen(false)
        _pageUrl.value = url
        _pageState.value = PageState.Loaded(siteLabelOf(url))
    }

    /** « Options avancées » d'un avertissement de sécurité : déplie ou replie le panneau. */
    fun toggleWarningOptions() {
        val isWarningShown = (_pageState.value as? PageState.Error)?.warning != null
        if (isWarningShown) setWarningOptionsOpen(!_layers.value.isWarningOptionsOpen)
    }

    private fun setWarningOptionsOpen(isOpen: Boolean) {
        _layers.update { it.copy(isWarningOptionsOpen = isOpen) }
    }

    /** « Retour en sécurité » : quitte l'avertissement comme Retour (page précédente, sinon accueil). */
    fun returnToSafety(canGoBack: Boolean) {
        setWarningOptionsOpen(false)
        onBack(canGoBack)
    }

    /**
     * « Afficher la page » / « Continuer malgré tout » : accorde l'exception de l'avertissement affiché (hôte, et
     * empreinte du certificat pour un certificat) pour la session, ou sur la TV si [isRemembered], puis recharge la
     * page. Sans exception descriptible (hôte ou certificat illisible), rien n'est accordé.
     */
    fun continueDespiteWarning(isRemembered: Boolean) {
        val warning = (_pageState.value as? PageState.Error)?.warning ?: return
        val exception = warning.exception ?: return
        viewModelScope.launch {
            val result = securityExceptions.allow(exception, isRemembered)
            if (result is Resource.Error) {
                // Le message d'échec ne doit pas être remplacé par « page non sécurisée » : le site est déjà annoncé.
                announcedInsecureHost = exception.host
                showNotice(BrowserNotice.SecurityExceptionNotSaved)
            }
            load(warning.url)
        }
    }

    /** Le D-pad pilote désormais la barre d'outils (aussi appelé par « maintenir Haut », #8). */
    fun enterInterfaceMode() {
        _layers.update { it.copy(mode = BrowserMode.Interface) }
    }

    /** Le D-pad pilote de nouveau la page. */
    fun exitInterfaceMode() {
        _layers.update { it.copy(mode = BrowserMode.Page) }
        showCursorHelp()
    }

    /** L'aide du mode curseur s'affiche à chaque retour dans la page, puis s'efface seule après [CURSOR_HELP_DURATION]. */
    private fun showCursorHelp() {
        cursorHelpJob?.cancel()
        cursorHelpJob = viewModelScope.launch {
            _isCursorHelpRequested.value = true
            delay(CURSOR_HELP_DURATION)
            _isCursorHelpRequested.value = false
        }
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

    /** « Historique » du menu : l'écran Historique remplace le menu, au-dessus de la page restée vivante dessous. */
    fun openHistory() {
        val isPageInFullscreen = _layers.value.isFullscreen
        if (isPageInFullscreen) return
        _layers.update { it.copy(isMenuOpen = false, isHistoryOpen = true) }
    }

    /** Retour ou bouton retour de l'Historique : la page est intacte, l'écran rend le focus (cf. BrowserFocus). */
    fun closeHistory() {
        _layers.update { it.copy(isHistoryOpen = false) }
    }

    /**
     * Page choisie dans l'Historique : chargée dans la même WebView, par le même chemin que toute adresse
     * ([load]) — donc avec les règles de sécurité habituelles (avertissement HTTP sans exception…).
     */
    fun onHistoryEntryChosen(url: String) {
        _layers.update { it.copy(isHistoryOpen = false, mode = BrowserMode.Page) }
        showCursorHelp()
        load(url)
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

    /**
     * Zoom choisi dans le menu pour le site de la page : ferme le menu (la page et le message restent visibles),
     * applique le niveau tout de suite (sans recharger) et le mémorise pour l'hôte exact, sauf si c'est le niveau
     * par défaut des Réglages, qui retire l'exception du site. Le message dit ce qui a été fait.
     */
    fun selectPageZoom(zoom: PageZoom) {
        closeMenu()
        val host = SiteHost.of(_pageUrl.value)
        if (host == null) {
            showNotice(BrowserNotice.SettingFailed)
            return
        }
        val isDefault = zoom == defaultZoom.value
        viewModelScope.launch {
            val result = if (isDefault) siteZooms.remove(host) else siteZooms.set(host, zoom)
            val notice = when {
                result is Resource.Error -> BrowserNotice.SettingFailed
                isDefault -> BrowserNotice.ZoomReset(zoom, host)
                else -> BrowserNotice.ZoomSaved(zoom, host)
            }
            showNotice(notice)
        }
    }

    /** Le menu n'a pas pu enregistrer un choix : un message bref l'explique, sans prendre le focus. */
    fun onMenuWriteFailed(failure: MenuWriteFailure) {
        val notice = when (failure) {
            MenuWriteFailure.AdBlock -> BrowserNotice.AdBlockFailed
            MenuWriteFailure.Bookmark -> BrowserNotice.BookmarkFailed
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
        showCursorHelp()
        load(url)
    }

    override fun onPageStarted(url: String) {
        pageTitle = ""
        recordedUrl = null
        _pageUrl.value = url
        val host = SiteHost.of(url)
        if (host != _unverifiedCertificateHost.value) _unverifiedCertificateHost.value = null
        if (host != announcedInsecureHost) announcedInsecureHost = null
        announceInsecureHttp(url, host)
        val isPageShown = when (val state = _pageState.value) {
            is PageState.Loaded -> true
            is PageState.Loading -> state.isPageShown
            is PageState.Error -> false
        }
        _pageState.value = PageState.Loading(siteLabelOf(url), progress = 0f, isPageShown = isPageShown)
    }

    override fun onUrlChanged(url: String, canGoBack: Boolean, canGoForward: Boolean) {
        committedUrl = url
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

    /**
     * Premier rendu visible : le voile de chargement se retire, la progression continue jusqu'à [onPageFinished].
     * Seul un chargement en cours est concerné : un avertissement ou une erreur (page HTTP / certificat refusés)
     * n'a rien de visible à révéler, et le voile n'est jamais retiré sous eux.
     */
    override fun onPageCommitVisible(url: String) {
        val state = _pageState.value
        if (state is PageState.Loading && !state.isPageShown) {
            _pageState.value = state.copy(isPageShown = true)
        }
    }

    override fun onPageFinished(url: String) {
        val state = _pageState.value
        // Après une erreur, WebView termine quand même « la page » (sa page d'erreur interne).
        val isShowingError = state is PageState.Error
        if (!isShowingError) {
            _pageState.value = PageState.Loaded(siteLabelOf(url))
            onPageVisited(url)
            recordVisit(url)
        }
    }

    override fun onTitleChanged(title: String) {
        pageTitle = title
        // Titre arrivé après la fin du chargement : il complète la visite déjà enregistrée.
        recordedUrl?.let(::recordVisit)
    }

    /** Enregistre la page affichée (écriture hors thread principal, échec sans conséquence pour la navigation). */
    private fun recordVisit(url: String) {
        recordedUrl = url
        val title = pageTitle
        viewModelScope.launch { pageVisits.recordVisit(url, title) }
    }

    override fun onLoadError(error: WebLoadError) {
        recordedUrl = null
        failedUrl = error.url ?: _pageUrl.value
        setWarningOptionsOpen(false)
        _pageState.value = PageState.Error(siteLabelOf(failedUrl), error.cause, error.warning)
    }

    override fun onUnverifiedCertificateAccepted(url: String) {
        val host = SiteHost.of(url)
        _unverifiedCertificateHost.value = host
        if (host != null && host != announcedInsecureHost) {
            announcedInsecureHost = host
            showNotice(BrowserNotice.UnverifiedCertificateShown)
        }
    }

    /** Un seul message « non sécurisé » par site : une page HTTP affichée par exception le dit à l'ouverture. */
    private fun announceInsecureHttp(url: String, host: String?) {
        val isInsecure = url.trim().startsWith("http://", ignoreCase = true)
        if (!isInsecure) return
        if (host == null || host == announcedInsecureHost) return
        announcedInsecureHost = host
        showNotice(BrowserNotice.InsecureHttpShown)
    }

    /** Seul chemin vers `loadUrl` : `loadUrl` contourne `shouldOverrideUrlLoading`, la politique est appliquée ici. */
    private fun load(url: String) {
        _pageUrl.value = url
        failedUrl = url
        setWarningOptionsOpen(false)
        val state = freshStateFor(url)
        _pageState.value = state
        val isAllowed = state is PageState.Loading
        if (isAllowed) _commands.trySend(BrowserCommand.Load(url))
    }

    private fun freshStateFor(url: String): PageState {
        val site = siteLabelOf(url)
        return when (val decision = WebNavigationPolicy.navigationOf(url, isMainFrame = true, securityExceptions)) {
            NavigationDecision.Allow -> PageState.Loading(site, progress = 0f)
            NavigationDecision.Refuse -> PageState.Error(site, LoadErrorCause.Unknown)
            is NavigationDecision.WarnHttp -> PageState.Error(site, LoadErrorCause.Unknown, decision.warningFor(url))
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        val NOTICE_DURATION = 4.seconds
        val CURSOR_HELP_DURATION = 8.seconds
    }
}

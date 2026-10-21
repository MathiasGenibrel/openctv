package com.openctv.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openctv.browser.core.designsystem.OpenCtvTheme
import com.openctv.browser.core.designsystem.StartupScreen
import com.openctv.browser.core.navigation.Destination
import com.openctv.browser.core.navigation.DestinationHost
import com.openctv.browser.core.navigation.ScopedViewModels
import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.history.PageVisitsContract
import com.openctv.browser.core.security.SecurityExceptionsContract
import com.openctv.browser.core.webview.LockedWebViewFactory
import com.openctv.browser.core.zoom.PageZoom
import com.openctv.browser.core.zoom.PageZoomContract
import com.openctv.browser.features.adblock.domain.AdBlockContract
import com.openctv.browser.features.bookmarks.domain.BookmarksContract
import com.openctv.browser.features.browser.ui.BrowserMenu
import com.openctv.browser.features.browser.ui.BrowserMenuViewModel
import com.openctv.browser.features.browser.ui.BrowserScreen
import com.openctv.browser.features.browser.ui.BrowserViewModel
import com.openctv.browser.features.browser.ui.PageKeyHandler
import com.openctv.browser.features.cursor.ui.CursorOverlay
import com.openctv.browser.features.cursor.ui.rememberCursorSession
import com.openctv.browser.features.history.domain.HistoryContract
import com.openctv.browser.features.history.ui.HistoryScreen
import com.openctv.browser.features.history.ui.HistoryViewModel
import com.openctv.browser.features.home.ui.HomeScreen
import com.openctv.browser.features.home.ui.HomeViewModel
import com.openctv.browser.features.search.domain.RecentSearchesContract
import com.openctv.browser.features.search.domain.SearchRequest
import com.openctv.browser.features.search.ui.SearchScreen
import com.openctv.browser.features.search.ui.SearchViewModel
import com.openctv.browser.features.settings.domain.SettingsContract
import com.openctv.browser.features.settings.domain.orDefaults
import com.openctv.browser.features.settings.ui.FilterListsStatus
import com.openctv.browser.features.settings.ui.SettingsScreen
import com.openctv.browser.features.settings.ui.SettingsViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Racine de composition : seul endroit qui résout les dépendances (Koin) et construit les ViewModels.
 * Les écrans reçoivent leur ViewModel et ne connaissent pas Koin.
 */
class MainActivity : ComponentActivity() {
    private val bookmarks: BookmarksContract by inject()
    private val settings: SettingsContract by inject()
    private val adBlock: AdBlockContract by inject()
    private val webViewFactory: LockedWebViewFactory by inject()
    private val recentSearches: RecentSearchesContract by inject()
    private val securityExceptions: SecurityExceptionsContract by inject()
    private val pageZoom: PageZoomContract by inject()
    private val history: HistoryContract by inject()
    private val pageVisits: PageVisitsContract by inject()

    /** Zoom des Réglages pour les sites sans zoom propre : suit les Réglages, lisible tout de suite. */
    private val defaultPageZoom: StateFlow<PageZoom> by lazy {
        settings.settings.map { it.orDefaults().pageZoom }.distinctUntilChanged()
            .stateIn(lifecycleScope, SharingStarted.Eagerly, settings.current.pageZoom)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Fenêtre de démarrage OpenCTV (thème Theme.OpenCTV.Starting) ; avant super.onCreate, elle bascule sur le thème normal.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Charge le moteur WebView en tâche de fond pendant l'écran de démarrage (sans effet s'il ne le gère pas).
        webViewFactory.warmUp(this)
        setContent {
            OpenCtvTheme {
                // Destination hissée ici : l'accueil ouvre un site, le navigateur revient à l'accueil.
                // `null` le temps de lire la page de démarrage ; restaurée telle quelle après une
                // recréation de l'activité (la page de démarrage n'est jamais réappliquée).
                var destination: Destination? by rememberSaveable(stateSaver = Destination.NullableSaver) {
                    mutableStateOf(null)
                }
                LaunchedEffect(Unit) {
                    if (destination == null) {
                        val launchUrl = settings.launchUrl()
                        // Exceptions mémorisées lues avant la page de démarrage (une page HTTP autorisée s'ouvre sans
                        // avertissement) ; lecture lancée dès l'Application, en parallèle de celle des préférences.
                        securityExceptions.persisted.first { it !is Resource.Loading }
                        // Idem pour les zooms par site : la page s'ouvre directement à son niveau, sans se remettre en page.
                        pageZoom.sites.first { it !is Resource.Loading }
                        destination = launchUrl?.let(Destination::Browser) ?: Destination.Home
                    }
                }
                val filterListsState by adBlock.filterLists.collectAsStateWithLifecycle()
                val filterListsStatus = FilterListsStatus(
                    lastUpdated = filterListsState.lastUpdated,
                    isUpdating = filterListsState.isUpdating,
                    hasFailed = filterListsState.error != null,
                )
                // L'accueil est la racine de la navigation : son ViewModel vit avec l'activité (et non
                // avec la destination) pour rendre le focus à l'élément quitté quand on y revient.
                val home = viewModel { HomeViewModel(bookmarks, settings) }
                // Écran OpenCTV tout de suite : la page de démarrage est lue (et la WebView prête) pendant ce temps.
                val resolved = destination ?: run {
                    StartupScreen()
                    return@OpenCtvTheme
                }
                // Autres ViewModels : créés dans la portée de la destination (cf. DestinationHost).
                DestinationHost(resolved) { current ->
                    when (current) {
                        Destination.Home -> HomeScreen(
                            viewModel = home,
                            onNavigate = { destination = it },
                            onExit = ::finish,
                            searchInput = { request ->
                                SearchInput(request, onOpen = home::onAddressChosen, onClose = home::onSearchClosed)
                            },
                        )
                        Destination.Settings -> SettingsScreen(
                            viewModel = viewModel { SettingsViewModel(settings, bookmarks, securityExceptions, pageZoom) },
                            filterLists = filterListsStatus,
                            // lifecycleScope : la mise à jour continue si l'utilisateur quitte les Réglages.
                            onRefreshFilterLists = { lifecycleScope.launch { adBlock.refreshNow() } },
                            onClose = { destination = Destination.Home },
                        )
                        is Destination.History -> HistoryScreen(
                            viewModel = viewModel { HistoryViewModel(history, focusedEntryId = current.focusedEntryId) },
                            // Retour depuis la page ouverte ramène ici, sur cette entrée (cf. Destination.Browser.returnTo).
                            onOpen = { entry -> destination = Destination.Browser(entry.url, openedFromHistoryEntry = entry.id) },
                            onClose = { destination = Destination.Home },
                        )
                        is Destination.Browser -> {
                            val browser = viewModel {
                                BrowserViewModel(
                                    current.url,
                                    bookmarks,
                                    onPageVisited = settings::recordLastPage,
                                    hudHideDelay = { settings.current.hudHideDelay.delay },
                                    securityExceptions = securityExceptions,
                                    siteZooms = pageZoom,
                                    defaultZoom = defaultPageZoom,
                                    pageVisits = pageVisits,
                                )
                            }
                            // Curseur virtuel : la racine relie les features (le navigateur n'importe pas le curseur).
                            val cursor = rememberCursorSession(speedFactor = { settings.current.cursorSpeed.factor })
                            BrowserScreen(
                                pageKeys = PageKeyHandler { event -> cursor.onKeyEvent(event) },
                                cursor = { webView, isActive, isVisible ->
                                    CursorOverlay(
                                        session = cursor,
                                        webView = webView,
                                        isActive = isActive,
                                        isVisible = isVisible,
                                        onEnterInterface = browser::enterInterfaceMode,
                                    )
                                },
                                webViewFactory = webViewFactory,
                                viewModel = browser,
                                onClose = { destination = current.returnTo },
                                onHome = { destination = Destination.Home },
                                menu = {
                                    val menu = viewModel {
                                        BrowserMenuViewModel(
                                            pageUrl = browser.pageUrl,
                                            settings = settings,
                                            adBlock = adBlock,
                                            bookmarks = bookmarks,
                                            onWriteFailed = browser::onMenuWriteFailed,
                                            pageZoom = browser.pageZoom,
                                            onZoomChosen = browser::selectPageZoom,
                                        )
                                    }
                                    BrowserMenu(
                                        viewModel = menu,
                                        onHome = browser::goHome,
                                        onHistory = browser::openHistory,
                                        onClose = browser::closeMenu,
                                    )
                                },
                                // Historique en surcouche : la page reste vivante dessous ; ViewModel neuf à chaque ouverture.
                                history = {
                                    ScopedViewModels("history") {
                                        HistoryScreen(
                                            viewModel = viewModel { HistoryViewModel(history) },
                                            onOpen = { entry -> browser.onHistoryEntryChosen(entry.url) },
                                            onClose = browser::closeHistory,
                                        )
                                    }
                                },
                                addressInput = { request ->
                                    SearchInput(
                                        request,
                                        onOpen = browser::onAddressChosen,
                                        onClose = browser::closeAddressInput,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    /** Saisie d'adresse / recherche en surcouche de l'écran qui l'ouvre, avec un ViewModel neuf à chaque ouverture. */
    @Composable
    private fun SearchInput(request: SearchRequest, onOpen: (String) -> Unit, onClose: () -> Unit) {
        ScopedViewModels(request) {
            SearchScreen(
                viewModel = viewModel { SearchViewModel(request, bookmarks, recentSearches, settings) },
                onOpen = onOpen,
                onClose = onClose,
            )
        }
    }
}

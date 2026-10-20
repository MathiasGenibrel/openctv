package com.openctv.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openctv.browser.core.designsystem.OpenCtvTheme
import com.openctv.browser.core.navigation.Destination
import com.openctv.browser.core.navigation.DestinationHost
import com.openctv.browser.core.webview.LockedWebViewFactory
import com.openctv.browser.features.adblock.domain.AdBlockContract
import com.openctv.browser.features.bookmarks.domain.BookmarksContract
import com.openctv.browser.features.browser.ui.BrowserScreen
import com.openctv.browser.features.browser.ui.BrowserViewModel
import com.openctv.browser.features.home.ui.HomeScreen
import com.openctv.browser.features.home.ui.HomeViewModel
import com.openctv.browser.features.settings.domain.SettingsContract
import com.openctv.browser.features.settings.ui.FilterListsStatus
import com.openctv.browser.features.settings.ui.SettingsScreen
import com.openctv.browser.features.settings.ui.SettingsViewModel
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                        destination = launchUrl?.let(Destination::Browser) ?: Destination.Home
                    }
                }
                val filterListsState by adBlock.filterLists.collectAsStateWithLifecycle()
                val filterListsStatus = FilterListsStatus(
                    lastUpdated = filterListsState.lastUpdated,
                    isUpdating = filterListsState.isUpdating,
                    hasFailed = filterListsState.error != null,
                )
                val resolved = destination ?: return@OpenCtvTheme
                // ViewModels créés dans la portée de la destination (cf. DestinationHost).
                DestinationHost(resolved) { current ->
                    when (current) {
                        Destination.Home -> HomeScreen(
                            viewModel = viewModel { HomeViewModel(bookmarks, settings) },
                            onNavigate = { destination = it },
                            onExit = ::finish,
                            // TODO(#5) saisie d'adresse/recherche.
                            onOpenSearch = {},
                            onAddBookmark = {},
                            onOpenSettings = { destination = Destination.Settings },
                        )
                        Destination.Settings -> SettingsScreen(
                            viewModel = viewModel { SettingsViewModel(settings, bookmarks) },
                            filterLists = filterListsStatus,
                            // lifecycleScope : la mise à jour continue si l'utilisateur quitte les Réglages.
                            onRefreshFilterLists = { lifecycleScope.launch { adBlock.refreshNow() } },
                            onClose = { destination = Destination.Home },
                        )
                        is Destination.Browser -> BrowserScreen(
                            webViewFactory = webViewFactory,
                            viewModel = viewModel { BrowserViewModel(current.url, onPageVisited = settings::recordLastPage) },
                            onClose = { destination = Destination.Home },
                        )
                    }
                }
            }
        }
    }
}

package com.openctv.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.openctv.browser.core.designsystem.OpenCtvTheme
import com.openctv.browser.core.navigation.Destination
import com.openctv.browser.core.navigation.DestinationHost
import com.openctv.browser.features.bookmarks.domain.BookmarksContract
import com.openctv.browser.features.browser.ui.BrowserScreen
import com.openctv.browser.features.home.ui.HomeScreen
import com.openctv.browser.features.home.ui.HomeViewModel
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val bookmarks: BookmarksContract by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenCtvTheme {
                // Destination hissée ici : l'accueil ouvre un site, le navigateur revient à l'accueil.
                var destination: Destination by rememberSaveable(stateSaver = Destination.Saver) {
                    mutableStateOf(Destination.Home)
                }
                DestinationHost(destination) { current ->
                    when (current) {
                        Destination.Home -> {
                            val viewModel: HomeViewModel = viewModel(
                                factory = viewModelFactory { initializer { HomeViewModel(bookmarks) } },
                            )
                            HomeScreen(
                                viewModel = viewModel,
                                onNavigate = { destination = it },
                                onExit = ::finish,
                                // TODO(#5) saisie d'adresse/recherche ; TODO(#11) écran Réglages.
                                onOpenSearch = {},
                                onAddBookmark = {},
                                onOpenSettings = {},
                            )
                        }
                        is Destination.Browser -> BrowserScreen(
                            url = current.url,
                            onClose = { destination = Destination.Home },
                        )
                    }
                }
            }
        }
    }
}

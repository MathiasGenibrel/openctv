package com.openctv.browser.features.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openctv.browser.R
import com.openctv.browser.core.designsystem.OverscanSafeArea
import com.openctv.browser.core.webview.LockedWebViewFactory
import com.openctv.browser.features.browser.domain.BrowserNotice
import com.openctv.browser.features.browser.domain.PageState
import com.openctv.browser.features.search.domain.SearchRequest

/**
 * Écran de navigation de [viewModel] (portée = la destination, cf. DestinationHost : un ViewModel neuf à
 * chaque ouverture).
 *
 * Le ViewModel décide, l'écran exécute. Chaque responsabilité a son fichier :
 * - couches (saisie, menu, mode interface) et Retour : `domain/BrowserLayers.kt` ;
 * - interface affichée (barre, aide) : `domain/BrowserHud.kt` ;
 * - aiguillage des touches : [consumesKey] (`BrowserKeys.kt`) ;
 * - déplacements du focus : [rememberBrowserFocus] (`BrowserFocus.kt`) ;
 * - page, chargement, erreur : [BrowserContent] (`BrowserPage.kt`).
 *
 * [addressInput] (saisie d'adresse) et [menu] (contenu du menu, #10) sont des surcouches fournies par la
 * racine de composition : la WebView reste vivante dessous, et le focus revient à l'élément qui les a
 * ouvertes quand elles se ferment. [menu] doit piéger le focus (`focusTrap`).
 */
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    webViewFactory: LockedWebViewFactory,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    addressInput: @Composable (SearchRequest) -> Unit = {},
    menu: @Composable () -> Unit = {},
) {
    val pageState by viewModel.pageState.collectAsStateWithLifecycle()
    val layers by viewModel.layers.collectAsStateWithLifecycle()
    val hud by viewModel.hud.collectAsStateWithLifecycle()
    val toolbarState by viewModel.toolbar.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    // Une seule WebView vivante : créée une fois ici, détruite quand l'écran quitte la composition.
    val context = LocalContext.current
    val webView = remember { webViewFactory.create(context, viewModel) }
    WebViewLifecycle(webView)

    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel, webView) {
        viewModel.onWebViewReady()
        viewModel.commands.collect { command ->
            when (command) {
                is BrowserCommand.Load -> webView.loadUrl(command.url)
                BrowserCommand.GoBack -> webView.goBack()
                BrowserCommand.GoForward -> webView.goForward()
                BrowserCommand.Reload -> webView.reload()
                BrowserCommand.Stop -> webView.stopLoading()
                BrowserCommand.Close -> currentOnClose()
            }
        }
    }

    // La décision revient au ViewModel ; la vue ne fournit que l'état d'historique de sa WebView.
    // Saisie ouverte : c'est elle qui traite Retour (son BackHandler ferme la surcouche).
    BackHandler(enabled = !layers.isAddressInputOpen) { viewModel.onBack(canGoBack = webView.canGoBack()) }

    val focus = rememberBrowserFocus(viewModel, webView, layers)
    val toolbarActions = remember(viewModel) {
        ToolbarActions(
            onBack = viewModel::goBack,
            onForward = viewModel::goForward,
            onReloadOrStop = viewModel::onReloadOrStop,
            onEditAddress = viewModel::openAddressInput,
            onToggleBookmark = viewModel::toggleBookmark,
            onMenu = viewModel::openMenu,
        )
    }

    // Ordre d'empilement, du bas vers le haut : page (+ chargement / erreur) et barre d'outils, message
    // en bas d'écran, menu, saisie d'adresse.
    Box(modifier.fillMaxSize().onPreviewKeyEvent { event -> viewModel.consumesKey(event, webView) }) {
        BrowserContent(
            pageState = pageState,
            pageView = { PageView(webView, isVisible = pageState !is PageState.Error) },
            onRetry = viewModel::retry,
            onHome = onClose,
            retryFocus = focus.retry,
            toolbar = {
                if (hud.isToolbarShown) {
                    BrowserToolbar(
                        state = toolbarState,
                        actions = toolbarActions,
                        isInteractive = hud.isToolbarInteractive,
                        addressFocus = focus.address,
                        menuFocus = focus.menuButton,
                    )
                }
            },
        )
        val shownNotice = notice
        val bottomMessage = when {
            shownNotice != null -> stringResource(noticeTextRes(shownNotice))
            hud.isInterfaceHelpShown -> stringResource(R.string.browser_interface_help)
            else -> null
        }
        bottomMessage?.let { BottomSnackbar(it) }
        if (layers.isMenuOpen) menu()
        layers.addressInput?.let { addressInput(it) }
    }
}

@Composable
private fun BottomSnackbar(text: String) {
    OverscanSafeArea {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            HelpSnackbar(text = text)
        }
    }
}

private fun noticeTextRes(notice: BrowserNotice): Int = when (notice) {
    BrowserNotice.BookmarkFailed -> R.string.browser_notice_bookmark_failed
}

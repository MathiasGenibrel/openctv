package com.openctv.browser.features.browser.ui

import android.view.View
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.openctv.browser.R
import com.openctv.browser.core.designsystem.ExpressiveShape
import com.openctv.browser.core.designsystem.OpenCtvButton
import com.openctv.browser.core.designsystem.OpenCtvButtonStyle
import com.openctv.browser.core.designsystem.OpenCtvColors
import com.openctv.browser.core.designsystem.OpenCtvTheme
import com.openctv.browser.core.designsystem.OverscanSafeArea
import com.openctv.browser.core.designsystem.asShape
import com.openctv.browser.core.webview.LoadErrorCause
import com.openctv.browser.core.webview.LockedWebViewFactory
import com.openctv.browser.features.browser.domain.BrowserMode
import com.openctv.browser.features.browser.domain.PageState
import com.openctv.browser.features.search.domain.SearchRequest

/**
 * Écran de navigation de [viewModel] (portée = la destination, cf. DestinationHost : un ViewModel neuf à
 * chaque ouverture).
 *
 * Deux modes (cf. [BrowserMode]) : en mode page le D-pad appartient à la WebView ; en mode interface le
 * focus est dans [BrowserToolbar]. Le ViewModel décide (Haut en haut de page, touche Menu, Bas, Retour) ;
 * l'écran ne fait que lui transmettre les touches et déplacer le focus en conséquence.
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
    val addressInputRequest by viewModel.addressInput.collectAsStateWithLifecycle()
    val isAddressInputOpen = addressInputRequest != null
    val isMenuOpen by viewModel.isMenuOpen.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val toolbarState by viewModel.toolbar.collectAsStateWithLifecycle()

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
    BackHandler(enabled = !isAddressInputOpen) { viewModel.onBack(canGoBack = webView.canGoBack()) }

    val retryFocus = remember { FocusRequester() }
    val addressFocus = remember { FocusRequester() }
    val menuFocus = remember { FocusRequester() }

    val focusPage = {
        val isShowingError = viewModel.pageState.value is PageState.Error
        if (isShowingError) retryFocus.requestFocus() else webView.requestFocus()
    }
    val focusToolbarOr = { toolbarTarget: FocusRequester ->
        val isInterfaceMode = viewModel.mode.value == BrowserMode.Interface
        if (isInterfaceMode) toolbarTarget.requestFocus() else focusPage()
    }
    // Une surcouche refermée rend le focus à ce qui l'a ouverte (champ d'adresse, bouton Menu) en mode
    // interface, à la page sinon. Un changement de mode déplace le focus entre la page et la barre.
    AfterClosed(isAddressInputOpen) { focusToolbarOr(addressFocus) }
    AfterClosed(isMenuOpen) { focusToolbarOr(menuFocus) }
    OnChanged(mode) { focusToolbarOr(addressFocus) }
    // Recréation de l'activité en mode interface : le focus ne doit pas rester sans élément.
    LaunchedEffect(Unit) { if (viewModel.mode.value == BrowserMode.Interface) addressFocus.requestFocus() }

    val isInterfaceMode = mode == BrowserMode.Interface
    val isLoading = pageState is PageState.Loading
    val isToolbarShown = isInterfaceMode || isLoading
    val isHelpShown = isInterfaceMode && !isAddressInputOpen && !isMenuOpen
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

    Box(modifier.fillMaxSize().onPreviewKeyEvent { event -> viewModel.consumes(event, webView) }) {
        BrowserContent(
            pageState = pageState,
            pageView = { PageView(webView, isVisible = pageState !is PageState.Error) },
            onRetry = viewModel::retry,
            onHome = onClose,
            retryFocus = retryFocus,
            toolbar = {
                if (isToolbarShown) {
                    BrowserToolbar(
                        state = toolbarState,
                        actions = toolbarActions,
                        isInteractive = isInterfaceMode,
                        addressFocus = addressFocus,
                        menuFocus = menuFocus,
                    )
                }
            },
        )
        if (isHelpShown) {
            OverscanSafeArea {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    HelpSnackbar(text = stringResource(R.string.browser_interface_help))
                }
            }
        }
        if (isMenuOpen) menu()
        addressInputRequest?.let { addressInput(it) }
    }
}

/** Touches de navigation entre page et interface : le ViewModel décide, `true` = événement consommé. */
private fun BrowserViewModel.consumes(event: KeyEvent, webView: WebView): Boolean {
    val isKeyDown = event.type == KeyEventType.KeyDown
    if (!isKeyDown) return false
    return when (event.key) {
        Key.DirectionUp -> onDpadUp(canScrollUp = webView.canScrollVertically(-1))
        Key.DirectionDown -> onDpadDown()
        Key.Menu -> onMenuKey()
        else -> false
    }
}

/** Appelle [action] à chaque fermeture d'une surcouche (pas à l'ouverture de l'écran). */
@Composable
private fun AfterClosed(isOpen: Boolean, action: () -> Unit) {
    OnChanged(isOpen) { nowOpen -> if (!nowOpen) action() }
}

/** Appelle [action] à chaque changement de [value], sauf à l'ouverture de l'écran. */
@Composable
private fun <T> OnChanged(value: T, action: (T) -> Unit) {
    var previous by remember { mutableStateOf(value) }
    val currentAction by rememberUpdatedState(action)
    LaunchedEffect(value) {
        val hasChanged = value != previous
        previous = value
        if (hasChanged) currentAction(value)
    }
}

@Composable
private fun PageView(webView: WebView, isVisible: Boolean) {
    AndroidView(
        factory = { webView },
        modifier = Modifier.fillMaxSize(),
        // Invisible = non focalisable : l'écran d'erreur garde le focus.
        update = { it.visibility = if (isVisible) View.VISIBLE else View.INVISIBLE },
    )
}

@Composable
private fun WebViewLifecycle(webView: WebView) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView.onPause()
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView.destroy()
        }
    }
}

/** Partie sans WebView ni ViewModel : prévisualisable. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BrowserContent(
    pageState: PageState,
    pageView: @Composable () -> Unit,
    onRetry: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    retryFocus: FocusRequester = remember { FocusRequester() },
    toolbar: @Composable () -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize().background(OpenCtvColors.Surface)) {
        pageView()
        when (pageState) {
            is PageState.Loading -> if (!pageState.isPageShown) LoadingContent(pageState.site)
            is PageState.Error -> ErrorContent(pageState, onRetry, onHome, retryFocus)
            is PageState.Loaded -> Unit
        }
        OverscanSafeArea {
            Column(Modifier.fillMaxWidth()) {
                toolbar()
                if (pageState is PageState.Loading) {
                    LinearWavyProgressIndicator(
                        progress = { pageState.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingContent(site: String) {
    Box(Modifier.fillMaxSize().background(OpenCtvColors.Surface), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ContainedLoadingIndicator(modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.browser_loading, site),
                style = MaterialTheme.typography.titleMedium,
                color = OpenCtvColors.OnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorContent(state: PageState.Error, onRetry: () -> Unit, onHome: () -> Unit, retryFocus: FocusRequester) {
    LaunchedEffect(Unit) { retryFocus.requestFocus() }

    Box(Modifier.fillMaxSize().background(OpenCtvColors.Surface), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(48.dp)) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .background(OpenCtvColors.ErrorContainer, ExpressiveShape.Cookie12.asShape()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "!",
                    style = MaterialTheme.typography.displaySmall,
                    color = OpenCtvColors.OnErrorContainer,
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.browser_error_title, state.site),
                style = MaterialTheme.typography.headlineMedium,
                color = OpenCtvColors.OnSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(errorMessageRes(state.cause)),
                style = MaterialTheme.typography.bodyLarge,
                color = OpenCtvColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OpenCtvButton(onClick = onRetry, modifier = Modifier.focusRequester(retryFocus)) {
                    Text(stringResource(R.string.browser_error_retry))
                }
                OpenCtvButton(onClick = onHome, style = OpenCtvButtonStyle.Tonal) {
                    Text(stringResource(R.string.browser_error_home))
                }
            }
        }
    }
}

private fun errorMessageRes(cause: LoadErrorCause): Int = when (cause) {
    LoadErrorCause.Unreachable -> R.string.browser_error_unreachable
    LoadErrorCause.InvalidCertificate -> R.string.browser_error_certificate
    LoadErrorCause.Unknown -> R.string.browser_error_unknown
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun BrowserLoadingPreview() {
    OpenCtvTheme {
        BrowserContent(PageState.Loading("arte.tv", 0.4f), pageView = {}, onRetry = {}, onHome = {})
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun BrowserErrorPreview() {
    OpenCtvTheme {
        BrowserContent(
            PageState.Error("arte.tv", LoadErrorCause.Unreachable), pageView = {}, onRetry = {}, onHome = {},
        )
    }
}

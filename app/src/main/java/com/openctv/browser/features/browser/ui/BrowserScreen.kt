package com.openctv.browser.features.browser.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openctv.browser.R
import com.openctv.browser.core.designsystem.DPadIcon
import com.openctv.browser.core.designsystem.KeyHint
import com.openctv.browser.core.designsystem.OpenCtvColors
import com.openctv.browser.core.designsystem.OpenCtvShapes
import com.openctv.browser.core.designsystem.OverscanSafeArea
import com.openctv.browser.core.webview.LockedWebViewFactory
import com.openctv.browser.core.webview.PointerAwareWebView
import com.openctv.browser.features.browser.domain.BrowserNotice
import com.openctv.browser.features.browser.domain.PageState
import com.openctv.browser.features.search.domain.SearchRequest

/**
 * Écran de navigation de [viewModel] (portée = la destination, cf. DestinationHost : un ViewModel neuf à
 * chaque ouverture). [onClose] : Retour depuis la première page, vers l'écran d'où l'on vient (accueil ou
 * Historique) ; [onHome] : « Accueil » (menu, écran d'erreur), toujours vers l'accueil.
 *
 * Le ViewModel décide, l'écran exécute. Chaque responsabilité a son fichier :
 * - couches (saisie, menu, mode interface) et Retour : `domain/BrowserLayers.kt` ;
 * - interface affichée (barre, aide) : `domain/BrowserHud.kt` ;
 * - aiguillage des touches : [consumesKey] (`BrowserKeys.kt`) ;
 * - déplacements du focus : [rememberBrowserFocus] (`BrowserFocus.kt`) ;
 * - page, chargement, erreur : [BrowserContent] (`BrowserPage.kt`) ;
 * - vidéo plein écran : [BrowserFullscreen] (`BrowserFullscreen.kt`), ses touches : [FullscreenKeys].
 *
 * [addressInput] (saisie d'adresse), [menu] (contenu du menu, #10) et [history] (écran Historique, ouvert depuis le
 * menu) sont des surcouches fournies par la racine de composition : la WebView reste vivante dessous, et le focus
 * revient à l'élément qui les a ouvertes quand elles se ferment. [menu] et [history] doivent piéger le focus
 * (`focusTrap`) ; [history] traite Retour lui-même.
 *
 * Le curseur virtuel (#8) arrive par [pageKeys] (flèches et OK en mode page, avant la WebView) et par le slot
 * [cursor] (surcouche et injection des événements sur la WebView reçue ; `isActive` = le D-pad est dans la
 * page, sans surcouche ni erreur).
 */
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    webViewFactory: LockedWebViewFactory,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onHome: () -> Unit = onClose,
    addressInput: @Composable (SearchRequest) -> Unit = {},
    menu: @Composable () -> Unit = {},
    history: @Composable () -> Unit = {},
    pageKeys: PageKeyHandler = PageKeyHandler.None,
    cursor: @Composable (webView: PointerAwareWebView, isActive: Boolean, isVisible: Boolean) -> Unit = { _, _, _ -> },
) {
    val pageState by viewModel.pageState.collectAsStateWithLifecycle()
    val layers by viewModel.layers.collectAsStateWithLifecycle()
    val hud by viewModel.hud.collectAsStateWithLifecycle()
    val toolbarState by viewModel.toolbar.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val pageZoom by viewModel.pageZoom.collectAsStateWithLifecycle()

    // Une seule WebView vivante : créée une fois ici, détruite quand l'écran quitte la composition.
    val context = LocalContext.current
    // Le plein écran vidéo naît et meurt avec la WebView : rendu à la page avant sa destruction (WebViewLifecycle).
    // Créés ensemble, une seule fois : l'hôte plein écran est un client de la WebView, et ses touches pilotent
    // cette même WebView (résolue au moment de l'appui, donc après sa création).
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val (fullscreen, webView) = remember {
        lateinit var created: PointerAwareWebView
        val presenter = context.findActivity()?.let { activity ->
            DecorFullscreenPresenter(activity, backDispatcher, onMediaCommand = { command -> created.runMediaCommand(command) })
        } ?: FullscreenPresenter.None
        val host = BrowserFullscreen(
            onShown = viewModel::onFullscreenShown,
            onHidden = viewModel::onFullscreenHidden,
            presenter = presenter,
        )
        created = webViewFactory.create(context, viewModel, host)
        host to created
    }
    WebViewLifecycle(webView, fullscreen)

    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnHome by rememberUpdatedState(onHome)
    LaunchedEffect(viewModel, webView) {
        viewModel.onWebViewReady()
        viewModel.commands.collect { command ->
            when (command) {
                is BrowserCommand.Load -> webView.loadUrl(command.url)
                BrowserCommand.GoBack -> webView.goBack()
                BrowserCommand.GoForward -> webView.goForward()
                BrowserCommand.Reload -> webView.reload()
                BrowserCommand.Stop -> webView.stopLoading()
                BrowserCommand.ExitFullscreen -> fullscreen.exit()
                BrowserCommand.Close -> currentOnClose()
                BrowserCommand.GoHome -> currentOnHome()
            }
        }
    }

    // La décision revient au ViewModel ; la vue ne fournit que l'état d'historique de sa WebView.
    // Saisie ou historique ouverts : c'est eux qui traitent Retour (leur BackHandler ferme la surcouche).
    BackHandler(enabled = !layers.isAddressInputOpen && !layers.isHistoryOpen) { viewModel.onBack(canGoBack = webView.canGoBack()) }

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

    val warningActions = remember(viewModel, webView) {
        SecurityWarningActions(
            onReturnToSafety = { viewModel.returnToSafety(canGoBack = webView.canGoBack()) },
            onToggleOptions = viewModel::toggleWarningOptions,
            onContinue = viewModel::continueDespiteWarning,
        )
    }

    val hudAlpha = rememberHudAlpha(isHidden = hud.isHidden)

    // Ordre d'empilement, du bas vers le haut : page (+ chargement / erreur) et barre d'outils, message
    // en bas d'écran, menu, saisie d'adresse. La vidéo plein écran est hors de cette pile, au-dessus de tout
    // (montée dans le decorView par DecorFullscreenPresenter) ; seul le mode immersif suit son état ici.
    Box(modifier.fillMaxSize().onPreviewKeyEvent { event -> viewModel.consumesKey(event, webView, pageKeys) }) {
        BrowserContent(
            pageState = pageState,
            pageView = { PageView(webView, pageZoom, isVisible = pageState !is PageState.Error) },
            onRetry = viewModel::retry,
            onHome = onHome,
            retryFocus = focus.retry,
            warningActions = warningActions,
            isWarningOptionsOpen = layers.isWarningOptionsOpen,
            toolbar = {
                if (hud.isToolbarShown) {
                    // Masquée par fondu mais toujours composée : le focus reste sur son élément.
                    BrowserToolbar(
                        modifier = Modifier.hudFade(hudAlpha),
                        state = toolbarState,
                        actions = toolbarActions,
                        isInteractive = hud.isToolbarInteractive,
                        addressFocus = focus.address,
                        menuFocus = focus.menuButton,
                    )
                }
            },
        )
        cursor(webView, hud.isCursorVisible, hud.isCursorShown)
        val shownNotice = notice
        val bottomMessage = when {
            shownNotice != null -> noticeText(shownNotice)
            hud.isInterfaceHelpShown -> stringResource(R.string.browser_interface_help)
            hud.isCursorHelpShown -> stringResource(R.string.browser_cursor_help)
            else -> null
        }
        val bottomHint = if (shownNotice == null && hud.isCursorHelpShown) {
            stringResource(R.string.browser_cursor_hold_up_hint)
        } else {
            null
        }
        bottomMessage?.let { BottomSnackbar(it, bottomHint, Modifier.hudFade(hudAlpha)) }
        // Plein écran : le menu (son Retour, son piège à focus) laisse la main au lecteur, et revient après.
        val isMenuShown = layers.isMenuOpen && !layers.isFullscreen
        if (isMenuShown) menu()
        val isHistoryShown = layers.isHistoryOpen && !layers.isFullscreen
        if (isHistoryShown) history()
        layers.addressInput?.let { addressInput(it) }
        if (fullscreen.view != null) ImmersiveMode()
    }
}

@Composable
private fun BottomSnackbar(text: String, hint: String? = null, modifier: Modifier = Modifier) {
    OverscanSafeArea(modifier) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                hint?.let { KeyHintChip(it) }
                HelpSnackbar(text = text)
            }
        }
    }
}

/** Astuce de touche (« Maintenir Haut : barre d'adresse ») au-dessus de la snackbar d'aide. */
@Composable
private fun KeyHintChip(label: String) {
    KeyHint(
        label = label,
        modifier = Modifier
            .background(OpenCtvColors.SurfaceContainerHigh, OpenCtvShapes.Full)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) { DPadIcon() }
}

/** Texte du message bref [notice] (les messages de zoom disent le niveau et l'hôte). */
@Composable
private fun noticeText(notice: BrowserNotice): String = when (notice) {
    BrowserNotice.BookmarkFailed -> stringResource(R.string.browser_notice_bookmark_failed)
    BrowserNotice.AdBlockFailed -> stringResource(R.string.browser_notice_adblock_failed)
    BrowserNotice.SettingFailed -> stringResource(R.string.browser_notice_setting_failed)
    BrowserNotice.InsecureHttpShown -> stringResource(R.string.security_notice_http_shown)
    BrowserNotice.UnverifiedCertificateShown -> stringResource(R.string.security_notice_certificate_shown)
    BrowserNotice.SecurityExceptionNotSaved -> stringResource(R.string.security_notice_not_saved)
    is BrowserNotice.ZoomSaved -> stringResource(R.string.browser_notice_zoom_saved, notice.zoom.percent, notice.host)
    is BrowserNotice.ZoomReset -> stringResource(R.string.browser_notice_zoom_reset, notice.zoom.percent, notice.host)
}

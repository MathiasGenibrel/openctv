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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.openctv.browser.R
import com.openctv.browser.core.designsystem.ExpressiveShape
import com.openctv.browser.core.designsystem.OpenCtvColors
import com.openctv.browser.core.designsystem.OpenCtvTheme
import com.openctv.browser.core.designsystem.OverscanSafeArea
import com.openctv.browser.core.designsystem.asShape
import com.openctv.browser.core.designsystem.focusRingBorder
import com.openctv.browser.core.webview.LoadErrorCause
import com.openctv.browser.core.webview.LockedWebViewFactory
import com.openctv.browser.features.browser.domain.PageState

/**
 * Écran de navigation pour [url]. [toolbar] est l'emplacement de la barre d'outils (ajoutée par une
 * tâche ultérieure) ; la barre de progression s'affiche juste dessous.
 */
@Composable
fun BrowserScreen(
    url: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    toolbar: @Composable () -> Unit = {},
) {
    // Portée = la destination (cf. DestinationHost) : un ViewModel neuf à chaque ouverture.
    val viewModel: BrowserViewModel = viewModel(
        factory = viewModelFactory { initializer { BrowserViewModel(url) } },
    )
    val pageState by viewModel.pageState.collectAsStateWithLifecycle()

    // Une seule WebView vivante : créée une fois ici, détruite quand l'écran quitte la composition.
    val context = LocalContext.current
    val webView = remember { LockedWebViewFactory().create(context, viewModel) }
    WebViewLifecycle(webView)

    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel, webView) {
        viewModel.onWebViewReady()
        viewModel.commands.collect { command ->
            when (command) {
                is BrowserCommand.Load -> webView.loadUrl(command.url)
                BrowserCommand.GoBack -> webView.goBack()
                BrowserCommand.Close -> currentOnClose()
            }
        }
    }

    // La décision revient au ViewModel ; la vue ne fournit que l'état d'historique de sa WebView.
    BackHandler { viewModel.onBack(canGoBack = webView.canGoBack()) }

    BrowserContent(
        pageState = pageState,
        pageView = { PageView(webView, isVisible = pageState !is PageState.Error) },
        onRetry = viewModel::retry,
        onHome = onClose,
        modifier = modifier,
        toolbar = toolbar,
    )
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
    toolbar: @Composable () -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize().background(OpenCtvColors.Surface)) {
        pageView()
        when (pageState) {
            is PageState.Loading -> if (!pageState.isPageShown) LoadingContent(pageState.site)
            is PageState.Error -> ErrorContent(pageState, onRetry, onHome)
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
private fun ErrorContent(state: PageState.Error, onRetry: () -> Unit, onHome: () -> Unit) {
    val retryFocus = remember { FocusRequester() }
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
                Button(
                    onClick = onRetry,
                    modifier = Modifier.focusRequester(retryFocus),
                    shape = ButtonDefaults.shape(CircleShape),
                    border = ButtonDefaults.border(focusedBorder = focusRingBorder(CircleShape)),
                    colors = ButtonDefaults.colors(
                        containerColor = OpenCtvColors.Primary,
                        contentColor = OpenCtvColors.OnPrimary,
                        focusedContainerColor = OpenCtvColors.Primary,
                        focusedContentColor = OpenCtvColors.OnPrimary,
                    ),
                ) { Text(stringResource(R.string.browser_error_retry)) }
                Button(
                    onClick = onHome,
                    shape = ButtonDefaults.shape(CircleShape),
                    border = ButtonDefaults.border(focusedBorder = focusRingBorder(CircleShape)),
                    colors = ButtonDefaults.colors(
                        containerColor = OpenCtvColors.SecondaryContainer,
                        contentColor = OpenCtvColors.OnSecondaryContainer,
                        focusedContainerColor = OpenCtvColors.SecondaryContainer,
                        focusedContentColor = OpenCtvColors.OnSecondaryContainer,
                    ),
                ) { Text(stringResource(R.string.browser_error_home)) }
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

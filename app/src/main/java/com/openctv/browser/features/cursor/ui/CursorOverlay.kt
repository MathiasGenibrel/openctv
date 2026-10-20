package com.openctv.browser.features.cursor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import com.openctv.browser.core.webview.PointerAwareWebView
import com.openctv.browser.core.webview.PointerKind
import com.openctv.browser.features.cursor.domain.CursorConfig
import com.openctv.browser.features.cursor.domain.HoverTarget

/** Session du curseur pour cet écran, avec les distances du design converties en pixels de la TV. */
@Composable
fun rememberCursorSession(speedFactor: () -> Float): CursorSession {
    val density = LocalDensity.current.density
    val currentSpeedFactor by rememberUpdatedState(speedFactor)
    return remember(density) {
        val config = CursorConfig(
            baseSpeed = BASE_SPEED_DP * density,
            scrollSpeed = SCROLL_SPEED_DP * density,
            edgeInset = EDGE_INSET_DP * density,
        )
        CursorSession(config, speedFactor = { currentSpeedFactor() })
    }
}

/**
 * Surcouche du curseur au-dessus de la page [webView] (même zone) : boucle de frames, injection du survol et du
 * défilement, dessin du pointeur.
 *
 * @param isActive le D-pad est dans la page (mode page, sans surcouche ni erreur) : sinon le curseur ne prend
 * aucune touche et s'efface.
 * @param isVisible visibilité demandée (fondu) : à piloter par le masquage à l'inactivité (#9).
 * @param onEnterInterface « Haut » maintenu au bord haut d'une page déjà en haut : ouvre la barre d'adresse.
 */
@Composable
fun CursorOverlay(
    session: CursorSession,
    webView: PointerAwareWebView,
    isActive: Boolean,
    onEnterInterface: () -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
) {
    val visuals = remember { CursorVisuals() }
    val painter = remember { CursorPainter() }
    val currentOnEnterInterface by rememberUpdatedState(onEnterInterface)
    // Compteur de frames lu au dessin uniquement : il redessine le canvas sans recomposer.
    var frame by remember { mutableLongStateOf(0L) }
    var originX by remember { mutableFloatStateOf(0f) }
    var originY by remember { mutableFloatStateOf(0f) }

    // Synchrone avec la composition : les touches suivantes voient déjà le bon état (erreur, surcouche…).
    SideEffect {
        session.setActive(isActive)
        visuals.setVisible(isActive && isVisible)
    }
    LaunchedEffect(isActive, isVisible) { session.wake() }

    DisposableEffect(session, webView) {
        session.injector = PointerInjector(webView)
        webView.onPointerKindChanged = { kind -> session.setHoverTarget(kind.toHoverTarget()) }
        onDispose {
            session.controller.releaseAll()
            webView.onPointerKindChanged = null
            session.injector = null
        }
    }

    LaunchedEffect(session, webView, visuals) {
        var lastNanos = 0L
        while (true) {
            val isAtRest = session.controller.isIdle && visuals.isSettled(session.controller, session.hoverTarget, lastNanos)
            if (isAtRest) {
                session.awaitWakeup()
                session.controller.resetClock()
                lastNanos = 0L
            }
            val now = withFrameNanos { it }
            val dt = if (lastNanos == 0L) NOMINAL_DT else ((now - lastNanos) / 1e9f).coerceIn(0f, MAX_DT)
            lastNanos = now
            stepFrame(session, webView, visuals, dt, now, currentOnEnterInterface)
            frame = now
        }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val origin = coordinates.positionInWindow()
                originX = origin.x
                originY = origin.y
                session.controller.setBounds(origin.x, origin.y, origin.x + coordinates.size.width, origin.y + coordinates.size.height)
                session.wake()
            },
    ) {
        frame // lecture à la phase de dessin : invalide ce canvas à chaque frame calculée
        with(painter) { drawCursor(visuals, originX, originY) }
    }
}

private fun stepFrame(
    session: CursorSession,
    webView: PointerAwareWebView,
    visuals: CursorVisuals,
    dt: Float,
    now: Long,
    onEnterInterface: () -> Unit,
) {
    val controller = session.controller
    val canScrollUp = webView.canScrollVertically(-1)
    val moved = controller.step(now, canScrollUp)
    val hasScrolled = controller.scrollDx != 0 || controller.scrollDy != 0
    if (hasScrolled) webView.scrollBy(controller.scrollDx, controller.scrollDy)
    // La page a bougé sous le pointeur, ou le pointeur sous la page : dans les deux cas elle doit le savoir.
    if (moved || hasScrolled) session.injectHover()
    visuals.update(controller, session.hoverTarget, dt, now)
    if (controller.consumeInterfaceRequest()) {
        controller.releaseAll()
        onEnterInterface()
    }
}

private fun PointerKind.toHoverTarget(): HoverTarget = when (this) {
    PointerKind.Default -> HoverTarget.None
    PointerKind.Clickable -> HoverTarget.Clickable
    PointerKind.Text -> HoverTarget.Text
}

private const val BASE_SPEED_DP = 420f
private const val SCROLL_SPEED_DP = 520f
private const val EDGE_INSET_DP = 10f
private const val NOMINAL_DT = 1f / 60f
private const val MAX_DT = 0.05f

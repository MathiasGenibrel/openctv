package com.openctv.browser.features.cursor.domain

import kotlin.math.min

/**
 * Logique du curseur virtuel, en Kotlin pur : position bornée à la page, vitesse, accélération sur appui
 * long, détection de bord, défilement et conversion vers les coordonnées de la WebView. Seule l'injection
 * des `MotionEvent` (vue) dépend d'Android.
 *
 * Pilotage : [press]/[release] à chaque touche, puis [step] à chaque frame avec l'horloge de la frame
 * (injectable : les tests fournissent leurs propres instants). Aucune allocation dans [step].
 * Les coordonnées (x, y) sont celles de la fenêtre ; [setBounds] donne la zone de la page, dont le coin haut
 * gauche est l'origine des coordonnées WebView.
 */
class CursorController(private val config: CursorConfig) {
    /** Multiplicateur du réglage « Vitesse du curseur » (`CursorSpeed.factor`). */
    var speedFactor: Float = 1f

    var x: Float = 0f
        private set
    var y: Float = 0f
        private set

    /** Vitesse instantanée en px/s (étirement et traînée du pointeur). */
    var velocityX: Float = 0f
        private set
    var velocityY: Float = 0f
        private set

    /** Progression de l'accélération, 0 (appui court) à 1 (vitesse maximale). */
    var acceleration: Float = 0f
        private set

    /** Pixels dont la page doit défiler pendant la dernière frame (entiers : la fraction est reportée). */
    var scrollDx: Int = 0
        private set
    var scrollDy: Int = 0
        private set

    /** Bord que le pointeur touche tant que la flèche correspondante est maintenue ; `null` sinon. */
    var edge: CursorDirection? = null
        private set

    /** Appui OK en cours : le pointeur est figé sur le point visé jusqu'au relâchement. */
    var isPressed: Boolean = false
        private set

    private var left = 0f
    private var top = 0f
    private var right = 0f
    private var bottom = 0f
    private var hasBounds = false

    private var held = 0
    private var lingering = 0
    private var movementStartNanos = 0L
    private var lastFrameNanos = NO_FRAME
    private var scrollRemainderX = 0f
    private var scrollRemainderY = 0f
    private var topHoldNanos = 0L
    private var isInterfaceRequested = false
    private var pressX = 0f
    private var pressY = 0f

    /** Rien à calculer : aucune flèche maintenue ni mouvement en cours ; la boucle de frames peut s'arrêter. */
    val isIdle: Boolean get() = (held or lingering) == 0 && velocityX == 0f && velocityY == 0f

    /**
     * Zone de la page. Le pointeur est centré au premier appel puis simplement ramené dans la zone
     * (changement de taille, rotation).
     */
    fun setBounds(left: Float, top: Float, right: Float, bottom: Float) {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
        if (!hasBounds) {
            x = (left + right) / 2f
            y = (top + bottom) / 2f
            hasBounds = true
        }
        clampToBounds()
    }

    fun press(direction: CursorDirection, nowNanos: Long) {
        if (held == 0) movementStartNanos = nowNanos
        held = held or direction.bit
        // Un appui plus court qu'une frame doit quand même déplacer le pointeur d'un pas.
        lingering = lingering or direction.bit
    }

    fun release(direction: CursorDirection) {
        held = held and direction.bit.inv()
    }

    /** Relâche tout (surcouche ouverte, changement de mode) : une touche relâchée ailleurs ne doit pas rester « enfoncée ». */
    fun releaseAll() {
        held = 0
        lingering = 0
        topHoldNanos = 0L
        // Un appui OK ouvert est clos par [releaseOk] côté appelant ; ici on ne laisse pas la vitesse résiduelle.
        stopMotion()
    }

    /** Remet l'horloge à zéro après une pause de la boucle de frames : la première frame compte pour un pas nominal. */
    fun resetClock() {
        lastFrameNanos = NO_FRAME
    }

    /** OK enfoncé : renvoie le `DOWN` au point visé et fige le pointeur. `null` si un appui est déjà ouvert. */
    fun pressOk(): PointerEvent? {
        if (isPressed) return null
        isPressed = true
        pressX = x
        pressY = y
        stopMotion()
        return PointerEvent(PointerAction.Down, pageX, pageY)
    }

    /** OK relâché : renvoie le `UP` au **même point** que le `DOWN`. `null` s'il n'y a pas d'appui ouvert. */
    fun releaseOk(): PointerEvent? {
        if (!isPressed) return null
        isPressed = false
        return PointerEvent(PointerAction.Up, pressX - left, pressY - top)
    }

    /**
     * Point visé en **coordonnées de la WebView**, pour le survol envoyé à chaque frame de mouvement (la page
     * applique son `:hover`). Deux nombres plutôt qu'un [PointerEvent] : rien n'est alloué par frame.
     */
    val pageX: Float get() = x - left
    val pageY: Float get() = y - top

    /**
     * « Haut » maintenu au bord haut d'une page déjà en haut : `true` une seule fois, quand il faut ouvrir le
     * mode interface (barre d'adresse).
     */
    fun consumeInterfaceRequest(): Boolean {
        val wasRequested = isInterfaceRequested
        isInterfaceRequested = false
        return wasRequested
    }

    /**
     * Avance d'une frame. [canScrollUp] : la page peut encore défiler vers le haut (sinon « Haut » au bord haut
     * mène au mode interface). Renvoie `true` si le pointeur a bougé (survol à renvoyer à la page).
     */
    fun step(nowNanos: Long, canScrollUp: Boolean): Boolean {
        val dtNanos = if (lastFrameNanos == NO_FRAME) NOMINAL_FRAME_NANOS else (nowNanos - lastFrameNanos)
        lastFrameNanos = nowNanos
        val dt = dtNanos.coerceIn(0L, MAX_FRAME_NANOS) / NANOS_PER_SECOND

        val active = held or lingering
        lingering = 0
        scrollDx = 0
        scrollDy = 0
        edge = null

        if (active == 0 || isPressed) {
            stopMotion()
            return false
        }

        val dirX = axis(active, CursorDirection.Right, CursorDirection.Left)
        val dirY = axis(active, CursorDirection.Down, CursorDirection.Up)
        val norm = if (dirX != 0 && dirY != 0) DIAGONAL else 1f

        acceleration = accelerationAt(nowNanos)
        val multiplier = 1f + (config.maxAcceleration - 1f) * acceleration
        val speed = config.baseSpeed * speedFactor * multiplier * norm
        val previousX = x
        val previousY = y
        x += dirX * speed * dt
        y += dirY * speed * dt
        clampToBounds()
        velocityX = if (dt > 0f) (x - previousX) / dt else 0f
        velocityY = if (dt > 0f) (y - previousY) / dt else 0f

        applyEdges(active, dirX, dirY, dt, dtNanos.coerceIn(0L, MAX_FRAME_NANOS), canScrollUp)
        return x != previousX || y != previousY
    }

    private fun applyEdges(active: Int, dirX: Int, dirY: Int, dt: Float, dtNanos: Long, canScrollUp: Boolean) {
        val isAtLeft = x <= left + config.edgeInset + EDGE_EPSILON
        val isAtRight = x >= right - config.edgeInset - EDGE_EPSILON
        val isAtTop = y <= top + config.edgeInset + EDGE_EPSILON
        val isAtBottom = y >= bottom - config.edgeInset - EDGE_EPSILON

        val isPushingLeft = dirX < 0 && isAtLeft
        val isPushingRight = dirX > 0 && isAtRight
        val isPushingUp = dirY < 0 && isAtTop
        val isPushingDown = dirY > 0 && isAtBottom

        edge = when {
            isPushingUp -> CursorDirection.Up
            isPushingDown -> CursorDirection.Down
            isPushingLeft -> CursorDirection.Left
            isPushingRight -> CursorDirection.Right
            else -> null
        }

        val scrollPerFrame = config.scrollSpeed * dt
        scrollRemainderX = scrollRemainderX.settle(if (isPushingLeft || isPushingRight) dirX * scrollPerFrame else 0f)
        scrollRemainderY = scrollRemainderY.settle(if (isPushingUp || isPushingDown) dirY * scrollPerFrame else 0f)
        scrollDx = scrollRemainderX.toInt()
        scrollDy = scrollRemainderY.toInt()
        scrollRemainderX -= scrollDx
        scrollRemainderY -= scrollDy

        val isHoldingUpOnTopOfPage = isPushingUp && !canScrollUp
        topHoldNanos = if (isHoldingUpOnTopOfPage) topHoldNanos + dtNanos else 0L
        if (topHoldNanos >= config.interfaceHoldNanos) {
            isInterfaceRequested = true
            topHoldNanos = 0L
        }
    }

    /** Un changement de sens vide le reste fractionnaire accumulé : pas de saut en repartant dans l'autre sens. */
    private fun Float.settle(delta: Float): Float = if (delta == 0f || (this != 0f && (this > 0f) != (delta > 0f))) delta else this + delta

    private fun accelerationAt(nowNanos: Long): Float {
        val heldNanos = nowNanos - movementStartNanos
        val rampedNanos = heldNanos - config.accelerationDelayNanos
        if (rampedNanos <= 0L) return 0f
        return min(1f, rampedNanos.toFloat() / config.accelerationRampNanos)
    }

    private fun axis(active: Int, positive: CursorDirection, negative: CursorDirection): Int {
        val isPositive = active and positive.bit != 0
        val isNegative = active and negative.bit != 0
        return if (isPositive == isNegative) 0 else if (isPositive) 1 else -1
    }

    private fun stopMotion() {
        velocityX = 0f
        velocityY = 0f
        acceleration = 0f
        edge = null
        scrollRemainderX = 0f
        scrollRemainderY = 0f
        topHoldNanos = 0L
    }

    private fun clampToBounds() {
        if (!hasBounds) return
        x = x.coerceIn(left + config.edgeInset, maxOf(left + config.edgeInset, right - config.edgeInset))
        y = y.coerceIn(top + config.edgeInset, maxOf(top + config.edgeInset, bottom - config.edgeInset))
    }

    private companion object {
        const val NO_FRAME = Long.MIN_VALUE
        const val NANOS_PER_SECOND = 1_000_000_000f
        const val NOMINAL_FRAME_NANOS = 16_666_667L
        const val MAX_FRAME_NANOS = 50_000_000L
        const val DIAGONAL = 0.70710678f
        const val EDGE_EPSILON = 0.5f
    }
}

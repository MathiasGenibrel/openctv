package com.openctv.browser.features.cursor

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.features.cursor.domain.CursorConfig
import com.openctv.browser.features.cursor.domain.CursorController
import com.openctv.browser.features.cursor.domain.CursorDirection
import com.openctv.browser.features.cursor.domain.PointerAction
import com.openctv.browser.features.cursor.domain.PointerEvent
import org.junit.Test

class CursorControllerTest {
    private val frame = 16_000_000L
    private val config = CursorConfig(baseSpeed = 1000f, scrollSpeed = 600f, edgeInset = 0f)
    private val controller = CursorController(config).apply { setBounds(0f, 0f, 1000f, 1000f) }
    private var now = 0L

    /** Avance de [count] frames de 16 ms, la page pouvant défiler vers le haut ou non. */
    private fun run(count: Int, canScrollUp: Boolean = true) = repeat(count) {
        now += frame
        controller.step(now, canScrollUp)
    }

    @Test
    fun `Given the pointer at the screen center When the user holds Right Then it moves right and stays at the same height`() {
        controller.press(CursorDirection.Right, now)
        run(5)

        assertThat(controller.x).isGreaterThan(500f)
        assertThat(controller.y).isEqualTo(500f)
    }

    @Test
    fun `Given a very short press When the key is released before the next frame Then the pointer still moves one step`() {
        controller.press(CursorDirection.Left, now)
        controller.release(CursorDirection.Left)
        run(3)

        assertThat(controller.x).isLessThan(500f)
        assertThat(controller.isIdle).isTrue()
    }

    @Test
    fun `Given the pointer near the right border When the user keeps pushing Right Then it stays inside the page`() {
        controller.press(CursorDirection.Right, now)
        run(120)

        assertThat(controller.x).isEqualTo(1000f)
    }

    @Test
    fun `Given a held arrow When 400 ms pass Then the pointer starts accelerating up to three times the base speed`() {
        controller.setBounds(0f, 0f, 100_000f, 1000f)
        controller.press(CursorDirection.Right, now)
        run(20) // ~320 ms : pas encore d'accélération
        assertThat(controller.acceleration).isEqualTo(0f)

        run(80) // largement après délai + rampe
        assertThat(controller.acceleration).isEqualTo(1f)
        assertThat(controller.velocityX).isWithin(1f).of(3000f)
    }

    @Test
    fun `Given the user releases the arrow When he presses it again Then the acceleration restarts from zero`() {
        controller.press(CursorDirection.Right, now)
        run(70)
        controller.release(CursorDirection.Right)
        run(2)

        controller.press(CursorDirection.Down, now)
        run(2)

        assertThat(controller.acceleration).isEqualTo(0f)
    }

    @Test
    fun `Given the slow and fast settings When the same key is held Then the pointer travels proportionally`() {
        fun distanceAt(factor: Float): Float {
            val c = CursorController(config).apply { setBounds(0f, 0f, 10_000f, 10_000f); speedFactor = factor }
            var t = 0L
            c.press(CursorDirection.Right, t)
            val start = c.x
            repeat(10) { t += frame; c.step(t, true) }
            return c.x - start
        }

        assertThat(distanceAt(0.6f)).isWithin(0.01f).of(distanceAt(1f) * 0.6f)
        assertThat(distanceAt(1.6f)).isWithin(0.01f).of(distanceAt(1f) * 1.6f)
    }

    @Test
    fun `Given the pointer on the bottom edge When the user holds Down Then the page scrolls down at a constant speed`() {
        controller.press(CursorDirection.Down, now)
        run(120) // atteint le bord
        var scrolled = 0
        repeat(60) {
            run(1)
            scrolled += controller.scrollDy
            assertThat(controller.edge).isEqualTo(CursorDirection.Down)
        }

        // 60 frames de 16 ms = 0,96 s à 600 px/s
        assertThat(scrolled).isAtLeast(570)
        assertThat(scrolled).isAtMost(580)
        assertThat(controller.y).isEqualTo(1000f)
    }

    @Test
    fun `Given the pointer in the middle When the user holds Down Then the page does not scroll`() {
        controller.press(CursorDirection.Down, now)
        run(5)

        assertThat(controller.scrollDy).isEqualTo(0)
        assertThat(controller.edge).isNull()
    }

    @Test
    fun `Given the pointer on the top edge of a page already at the top When Up is held long enough Then the interface is requested once`() {
        controller.press(CursorDirection.Up, now)
        run(90, canScrollUp = false) // atteint le bord haut, puis 600 ms de maintien

        assertThat(controller.consumeInterfaceRequest()).isTrue()
        assertThat(controller.consumeInterfaceRequest()).isFalse()
    }

    @Test
    fun `Given the pointer on the top edge of a page that can scroll up When Up is held Then the page scrolls and no interface is requested`() {
        controller.press(CursorDirection.Up, now)
        run(40, canScrollUp = true)

        assertThat(controller.consumeInterfaceRequest()).isFalse()
        assertThat(controller.scrollDy).isAtMost(0)
    }

    @Test
    fun `Given Up released before the hold delay at the top When it is pressed again Then the hold restarts`() {
        controller.press(CursorDirection.Up, now)
        run(40, canScrollUp = true) // au bord haut, la page défile encore
        run(25, canScrollUp = false) // ~400 ms en haut de page
        controller.release(CursorDirection.Up)
        run(2, canScrollUp = false)
        controller.press(CursorDirection.Up, now)
        run(10, canScrollUp = false)

        assertThat(controller.consumeInterfaceRequest()).isFalse()
    }

    @Test
    fun `Given the pointer over a button When OK is pressed and released Then the page receives DOWN then UP at the same point`() {
        controller.press(CursorDirection.Right, now)
        run(3)
        controller.release(CursorDirection.Right)
        run(2)

        val down = controller.pressOk()
        // Une flèche appuyée pendant le clic ne déplace pas le pointeur : le point visé reste le même.
        controller.press(CursorDirection.Down, now)
        run(5)
        val up = controller.releaseOk()

        assertThat(down?.action).isEqualTo(PointerAction.Down)
        assertThat(up?.action).isEqualTo(PointerAction.Up)
        assertThat(up?.x).isEqualTo(down?.x)
        assertThat(up?.y).isEqualTo(down?.y)
    }

    @Test
    fun `Given a click already open When OK repeats Then no second DOWN is sent`() {
        assertThat(controller.pressOk()).isNotNull()
        assertThat(controller.pressOk()).isNull()
        assertThat(controller.releaseOk()).isNotNull()
        assertThat(controller.releaseOk()).isNull()
    }

    @Test
    fun `Given a page area offset from the window origin When the pointer hovers Then the event uses WebView coordinates`() {
        val offset = CursorController(config).apply { setBounds(100f, 50f, 1100f, 1050f) }

        // centre de la zone : (600, 550) en fenêtre = (500, 500) dans la WebView
        assertThat(offset.pageX).isEqualTo(500f)
        assertThat(offset.pageY).isEqualTo(500f)
        assertThat(offset.pressOk()).isEqualTo(PointerEvent(PointerAction.Down, 500f, 500f))
    }

    @Test
    fun `Given the page area shrinks When the pointer was outside the new area Then it is brought back inside`() {
        controller.press(CursorDirection.Right, now)
        run(120)

        controller.setBounds(0f, 0f, 600f, 600f)

        assertThat(controller.x).isEqualTo(600f)
        assertThat(controller.y).isAtMost(600f)
    }

    @Test
    fun `Given the user opens a layer while holding an arrow When all keys are released Then the pointer stops`() {
        controller.press(CursorDirection.Right, now)
        run(3)

        controller.releaseAll()
        val before = controller.x
        run(5)

        assertThat(controller.x).isEqualTo(before)
        assertThat(controller.isIdle).isTrue()
    }
}

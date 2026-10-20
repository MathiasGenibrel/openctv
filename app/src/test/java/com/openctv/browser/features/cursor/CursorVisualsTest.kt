package com.openctv.browser.features.cursor

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.features.cursor.domain.CursorConfig
import com.openctv.browser.features.cursor.domain.CursorController
import com.openctv.browser.features.cursor.domain.CursorDirection
import com.openctv.browser.features.cursor.domain.HoverTarget
import com.openctv.browser.features.cursor.ui.CursorVisuals
import org.junit.Test
import kotlin.math.abs

/** Ce que l'utilisateur voit (pointeur dessiné) face à ce qui est cliqué (point visé du contrôleur). */
class CursorVisualsTest {
    private val frame = 16_000_000L
    private val config = CursorConfig(baseSpeed = 1000f, scrollSpeed = 600f, edgeInset = 0f)
    private val controller = CursorController(config).apply { setBounds(0f, 0f, 100_000f, 1000f) }
    private val visuals = CursorVisuals()
    private var now = 0L

    /** Avance de [count] frames de 16 ms ; renvoie le plus grand écart vu entre le dessin et le point visé. */
    private fun run(count: Int): Float {
        var maxGap = 0f
        repeat(count) {
            now += frame
            controller.step(now, canScrollUp = true)
            visuals.update(controller, HoverTarget.None, dtSeconds = frame / 1e9f, now = now)
            maxGap = maxOf(maxGap, abs(visuals.x.value - controller.x))
        }
        return maxGap
    }

    @Test
    fun `Given an arrow held until full acceleration When the pointer moves Then the drawn pointer stays on the aimed point`() {
        run(1) // premier dessin : le pointeur apparaît sur le point visé
        controller.press(CursorDirection.Right, now)
        run(15) // mise en vitesse : le ressort rattrape l'élan (c'est là, et à l'arrêt, que se voit son caractère)

        val maxGap = run(75) // rampe d'accélération jusqu'à ×3, soit 3000 px/s

        assertThat(controller.velocityX).isWithin(1f).of(3000f)
        // Sans suivi de la vitesse, un ressort fast spatial traînerait ici de plus de 120 px.
        assertThat(maxGap).isLessThan(6f)
    }

    @Test
    fun `Given the arrow released on a button When OK is pressed right away Then the click lands where the pointer comes to rest`() {
        run(1)
        controller.press(CursorDirection.Right, now)
        run(20)
        // Ce que l'utilisateur voit quand il relâche la flèche « sur le bouton ».
        val drawnAtRelease = visuals.x.value
        controller.release(CursorDirection.Right)
        run(1) // le ressort dépasse encore : OK arrive avant que le dessin soit posé

        val down = controller.pressOk()!!
        run(60)

        assertThat(abs(drawnAtRelease - down.x)).isLessThan(2f)
        assertThat(visuals.x.value).isWithin(0.5f).of(down.x)
        assertThat(controller.isIdle && visuals.isSettled(controller, HoverTarget.None, now)).isTrue()
    }
}

package com.openctv.browser.features.cursor

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.features.cursor.domain.SpringFollower
import org.junit.Test

class SpringFollowerTest {
    private fun SpringFollower.run(target: Float, frames: Int): Float {
        var peak = value
        repeat(frames) {
            step(target, 1f / 60f)
            peak = maxOf(peak, value)
        }
        return peak
    }

    @Test
    fun `Given the move spring When the target jumps Then it overshoots slightly and settles on it`() {
        val spring = SpringFollower(dampingRatio = 0.6f, stiffness = 800f)

        val peak = spring.run(target = 100f, frames = 120)

        assertThat(peak).isGreaterThan(100f)
        assertThat(spring.isSettledOn(100f)).isTrue()
    }

    @Test
    fun `Given the press spring When the target changes Then it reaches it without overshoot`() {
        val spring = SpringFollower(dampingRatio = 1f, stiffness = 3800f)

        val peak = spring.run(target = 1f, frames = 60)

        assertThat(peak).isAtMost(1.001f)
        assertThat(spring.isSettledOn(1f)).isTrue()
    }

    @Test
    fun `Given the move spring fed with the target speed When the target moves at constant speed Then it follows without lag`() {
        val spring = SpringFollower(dampingRatio = 0.6f, stiffness = 800f)
        val speed = 1200f
        var target = 0f

        repeat(60) {
            target += speed / 60f
            spring.step(target, 1f / 60f, targetVelocity = speed)
        }

        assertThat(spring.value).isWithin(1f).of(target)
    }
}

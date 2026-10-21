package com.openctv.browser.core.webview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaCommandTest {

    @Test
    fun `Given a video in fullscreen When any command is sent Then it aims at the fullscreen video before any other video of the page`() {
        MediaCommand.entries.forEach { command ->
            val script = command.script

            assertThat(script.indexOf("document.fullscreenElement"))
                .isLessThan(script.indexOf("document.querySelector('video')"))
        }
    }

    @Test
    fun `Given a page without a usable video When any command is sent Then it does nothing and fails silently`() {
        MediaCommand.entries.forEach { command ->
            assertThat(command.script).contains("if(!v)return;")
            assertThat(command.script).contains("catch(e){}")
        }
    }

    @Test
    fun `Given a video in fullscreen When the user seeks Then it moves by ten seconds without going before the start`() {
        assertThat(MediaCommand.SeekBack.script).contains("v.currentTime=Math.max(0,v.currentTime+(-10));")
        assertThat(MediaCommand.SeekForward.script).contains("v.currentTime=Math.max(0,v.currentTime+(10));")
    }
}

package com.openctv.browser.core.remote

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.webview.MediaCommand
import com.openctv.browser.core.webview.ScrollDirection
import org.junit.Test

class RemoteKeyMapTest {

    @Test
    fun `Given the channel and page keys When they are pressed Then they scroll one screen in their direction`() {
        assertThat(RemoteKeyMap.actionFor(KeyEvent.KEYCODE_CHANNEL_UP)).isEqualTo(RemoteAction.ScrollPage(ScrollDirection.Up))
        assertThat(RemoteKeyMap.actionFor(KeyEvent.KEYCODE_PAGE_UP)).isEqualTo(RemoteAction.ScrollPage(ScrollDirection.Up))
        assertThat(RemoteKeyMap.actionFor(KeyEvent.KEYCODE_CHANNEL_DOWN)).isEqualTo(RemoteAction.ScrollPage(ScrollDirection.Down))
        assertThat(RemoteKeyMap.actionFor(KeyEvent.KEYCODE_PAGE_DOWN)).isEqualTo(RemoteAction.ScrollPage(ScrollDirection.Down))
    }

    @Test
    fun `Given the media keys When they are pressed Then they map to the matching video command`() {
        val expected = mapOf(
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to MediaCommand.TogglePlayback,
            KeyEvent.KEYCODE_MEDIA_PLAY to MediaCommand.Play,
            KeyEvent.KEYCODE_MEDIA_PAUSE to MediaCommand.Pause,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD to MediaCommand.SeekForward,
            KeyEvent.KEYCODE_MEDIA_NEXT to MediaCommand.SeekForward,
            KeyEvent.KEYCODE_MEDIA_REWIND to MediaCommand.SeekBack,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS to MediaCommand.SeekBack,
        )
        expected.forEach { (keyCode, command) ->
            assertThat(RemoteKeyMap.actionFor(keyCode)).isEqualTo(RemoteAction.Media(command))
        }
    }

    @Test
    fun `Given the search and info keys When they are pressed Then they open the address input and the menu`() {
        assertThat(RemoteKeyMap.actionFor(KeyEvent.KEYCODE_SEARCH)).isEqualTo(RemoteAction.OpenSearch)
        assertThat(RemoteKeyMap.actionFor(KeyEvent.KEYCODE_ASSIST)).isEqualTo(RemoteAction.OpenSearch)
        assertThat(RemoteKeyMap.actionFor(KeyEvent.KEYCODE_INFO)).isEqualTo(RemoteAction.OpenMenu)
    }

    @Test
    fun `Given the digit keys When 1 to 9 are pressed Then they open the favorite of that rank and 0 does nothing`() {
        (1..9).forEach { digit ->
            assertThat(RemoteKeyMap.actionFor(KeyEvent.KEYCODE_0 + digit)).isEqualTo(RemoteAction.OpenFavorite(digit))
        }
        assertThat(RemoteKeyMap.actionFor(KeyEvent.KEYCODE_0)).isNull()
    }

    @Test
    fun `Given system and app keys When they are looked up Then none is ever intercepted`() {
        val untouchable = listOf(
            KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_MUTE, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_TV_INPUT,
            KeyEvent.KEYCODE_TV_INPUT_HDMI_1, KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_BUTTON_1, KeyEvent.KEYCODE_BUTTON_2, KeyEvent.KEYCODE_BUTTON_3, KeyEvent.KEYCODE_BUTTON_4,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MENU,
        )
        untouchable.forEach { keyCode -> assertThat(RemoteKeyMap.actionFor(keyCode)).isNull() }
    }

    @Test
    fun `Given a held key When it repeats Then only scrolling and seeking repeat`() {
        assertThat(RemoteAction.ScrollPage(ScrollDirection.Down).repeatsWhenHeld).isTrue()
        assertThat(RemoteAction.Media(MediaCommand.SeekForward).repeatsWhenHeld).isTrue()
        assertThat(RemoteAction.Media(MediaCommand.TogglePlayback).repeatsWhenHeld).isFalse()
        assertThat(RemoteAction.OpenFavorite(1).repeatsWhenHeld).isFalse()
        assertThat(RemoteAction.OpenSearch.repeatsWhenHeld).isFalse()
    }

    @Test
    fun `Given a text field of the page focused When a remote key is pressed Then only the keys that do not type keep their role`() {
        assertThat(RemoteAction.OpenFavorite(1).yieldsToPageTextField).isTrue()
        assertThat(RemoteAction.OpenSearch.yieldsToPageTextField).isTrue()
        assertThat(RemoteAction.ScrollPage(ScrollDirection.Down).yieldsToPageTextField).isTrue()
        assertThat(RemoteAction.Media(MediaCommand.TogglePlayback).yieldsToPageTextField).isFalse()
        assertThat(RemoteAction.OpenMenu.yieldsToPageTextField).isFalse()
    }
}

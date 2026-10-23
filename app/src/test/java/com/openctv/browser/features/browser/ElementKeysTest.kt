package com.openctv.browser.features.browser

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.features.browser.domain.ElementKey
import com.openctv.browser.features.browser.domain.ElementKeyAction
import com.openctv.browser.features.browser.domain.ElementKeys
import com.openctv.browser.features.browser.domain.KeyPhase
import com.openctv.browser.features.browser.domain.PadKey
import org.junit.Test

class ElementKeysTest {
    private val keys = ElementKeys()

    private fun press(key: PadKey, isTextFieldFocused: Boolean = false, isKeyboardShown: Boolean = false) =
        keys.actionFor(key, KeyPhase.Down, isTextFieldFocused, isKeyboardShown)

    private fun repeat(key: PadKey, isTextFieldFocused: Boolean = false, isKeyboardShown: Boolean = false) =
        keys.actionFor(key, KeyPhase.Repeat, isTextFieldFocused, isKeyboardShown)

    private fun release(key: PadKey, isTextFieldFocused: Boolean = false, isKeyboardShown: Boolean = false) =
        keys.actionFor(key, KeyPhase.Up, isTextFieldFocused, isKeyboardShown)

    @Test
    fun `Given the elements mode When the user presses Down or Right Then the next element is asked with Tab`() {
        assertThat(press(PadKey.Down)).isEqualTo(ElementKeyAction.Send(ElementKey.Next))
        assertThat(press(PadKey.Right)).isEqualTo(ElementKeyAction.Send(ElementKey.Next))
    }

    @Test
    fun `Given the elements mode When the user presses Left Then the previous element is asked with Shift Tab`() {
        assertThat(press(PadKey.Left)).isEqualTo(ElementKeyAction.Send(ElementKey.Previous))
    }

    @Test
    fun `Given the elements mode When the user briefly presses Up Then the previous element is asked on release only`() {
        assertThat(press(PadKey.Up)).isEqualTo(ElementKeyAction.Ignore)
        assertThat(release(PadKey.Up)).isEqualTo(ElementKeyAction.Send(ElementKey.Previous))
    }

    @Test
    fun `Given a link focused When the user presses OK Then Enter activates it`() {
        assertThat(press(PadKey.Ok)).isEqualTo(ElementKeyAction.Send(ElementKey.Activate))
    }

    @Test
    fun `Given a text field focused When the user presses OK Then the system keyboard opens`() {
        assertThat(press(PadKey.Ok, isTextFieldFocused = true)).isEqualTo(ElementKeyAction.OpenKeyboard)
    }

    @Test
    fun `Given a text field focused without keyboard When the user presses Left or Right Then the focus keeps moving between elements`() {
        assertThat(press(PadKey.Left, isTextFieldFocused = true)).isEqualTo(ElementKeyAction.Send(ElementKey.Previous))
        assertThat(press(PadKey.Right, isTextFieldFocused = true)).isEqualTo(ElementKeyAction.Send(ElementKey.Next))
    }

    @Test
    fun `Given the keyboard shown on a text field When the user presses Left or Right Then the keys move the text cursor`() {
        assertThat(press(PadKey.Left, isTextFieldFocused = true, isKeyboardShown = true)).isEqualTo(ElementKeyAction.PassThrough)
        assertThat(press(PadKey.Right, isTextFieldFocused = true, isKeyboardShown = true)).isEqualTo(ElementKeyAction.PassThrough)
        assertThat(release(PadKey.Left, isTextFieldFocused = true, isKeyboardShown = true)).isEqualTo(ElementKeyAction.PassThrough)
    }

    @Test
    fun `Given a keyboard that was shown on a field When Back closed it and the user presses Right Then the next element is asked`() {
        press(PadKey.Left, isTextFieldFocused = true, isKeyboardShown = true)

        val afterBack = press(PadKey.Right, isTextFieldFocused = true, isKeyboardShown = false)

        assertThat(afterBack).isEqualTo(ElementKeyAction.Send(ElementKey.Next))
    }

    @Test
    fun `Given the keyboard shown on a field When the user presses Up or Down Then the focus leaves the field`() {
        assertThat(press(PadKey.Down, isTextFieldFocused = true, isKeyboardShown = true))
            .isEqualTo(ElementKeyAction.Send(ElementKey.Next))
    }

    @Test
    fun `Given a text field focused When the user presses Up or Down Then the focus leaves the field`() {
        assertThat(press(PadKey.Down, isTextFieldFocused = true)).isEqualTo(ElementKeyAction.Send(ElementKey.Next))
        press(PadKey.Up, isTextFieldFocused = true)
        assertThat(release(PadKey.Up, isTextFieldFocused = true)).isEqualTo(ElementKeyAction.Send(ElementKey.Previous))
    }

    @Test
    fun `Given Up is held When the key repeats Then the address bar opens without moving the page focus`() {
        assertThat(press(PadKey.Up)).isEqualTo(ElementKeyAction.Ignore)
        assertThat(repeat(PadKey.Up)).isEqualTo(ElementKeyAction.OpenInterface)
        assertThat(repeat(PadKey.Up)).isEqualTo(ElementKeyAction.Ignore)
        assertThat(release(PadKey.Up)).isEqualTo(ElementKeyAction.Ignore)
    }

    @Test
    fun `Given Up is held in a text field When the key repeats Then the address bar opens`() {
        press(PadKey.Up, isTextFieldFocused = true)

        assertThat(repeat(PadKey.Up, isTextFieldFocused = true)).isEqualTo(ElementKeyAction.OpenInterface)
    }

    @Test
    fun `Given the press of Up was not seen When its repeat or release arrives Then nothing is sent to the page`() {
        assertThat(repeat(PadKey.Up)).isEqualTo(ElementKeyAction.Ignore)
        assertThat(release(PadKey.Up)).isEqualTo(ElementKeyAction.Ignore)
    }

    @Test
    fun `Given the address bar opened by holding Up When Up is pressed briefly again Then the previous element is asked`() {
        press(PadKey.Up)
        repeat(PadKey.Up)
        release(PadKey.Up)

        press(PadKey.Up)

        assertThat(release(PadKey.Up)).isEqualTo(ElementKeyAction.Send(ElementKey.Previous))
    }

    @Test
    fun `Given Down is held When the key repeats Then elements keep advancing`() {
        assertThat(repeat(PadKey.Down)).isEqualTo(ElementKeyAction.Send(ElementKey.Next))
    }

    @Test
    fun `Given OK is held When the key repeats Then the element is activated only once`() {
        assertThat(repeat(PadKey.Ok)).isEqualTo(ElementKeyAction.Ignore)
    }

    @Test
    fun `Given a key already handled When it is released Then nothing more happens`() {
        listOf(PadKey.Down, PadKey.Left, PadKey.Right, PadKey.Ok).forEach { key ->
            press(key)
            assertThat(release(key)).isEqualTo(ElementKeyAction.Ignore)
        }
    }
}

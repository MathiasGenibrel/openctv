package com.openctv.browser.features.browser

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.features.browser.domain.ElementKey
import com.openctv.browser.features.browser.domain.ElementKeyAction
import com.openctv.browser.features.browser.domain.ElementKeys
import com.openctv.browser.features.browser.domain.KeyPhase
import com.openctv.browser.features.browser.domain.PadKey
import org.junit.Test

class ElementKeysTest {
    private fun press(key: PadKey, isTextFieldFocused: Boolean = false) =
        ElementKeys.actionFor(key, KeyPhase.Down, isTextFieldFocused)

    @Test
    fun `Given the elements mode When the user presses Down or Right Then the next element is asked with Tab`() {
        assertThat(press(PadKey.Down)).isEqualTo(ElementKeyAction.Send(ElementKey.Next))
        assertThat(press(PadKey.Right)).isEqualTo(ElementKeyAction.Send(ElementKey.Next))
    }

    @Test
    fun `Given the elements mode When the user presses Up or Left Then the previous element is asked with Shift Tab`() {
        assertThat(press(PadKey.Up)).isEqualTo(ElementKeyAction.Send(ElementKey.Previous))
        assertThat(press(PadKey.Left)).isEqualTo(ElementKeyAction.Send(ElementKey.Previous))
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
    fun `Given a text field focused When the user presses Left or Right Then the keys move the text cursor`() {
        assertThat(press(PadKey.Left, isTextFieldFocused = true)).isEqualTo(ElementKeyAction.PassThrough)
        assertThat(press(PadKey.Right, isTextFieldFocused = true)).isEqualTo(ElementKeyAction.PassThrough)
        assertThat(ElementKeys.actionFor(PadKey.Left, KeyPhase.Up, isTextFieldFocused = true))
            .isEqualTo(ElementKeyAction.PassThrough)
    }

    @Test
    fun `Given a text field focused When the user presses Up or Down Then the focus leaves the field`() {
        assertThat(press(PadKey.Down, isTextFieldFocused = true)).isEqualTo(ElementKeyAction.Send(ElementKey.Next))
        assertThat(press(PadKey.Up, isTextFieldFocused = true)).isEqualTo(ElementKeyAction.Send(ElementKey.Previous))
    }

    @Test
    fun `Given Up is held When the key repeats Then the address bar opens`() {
        assertThat(ElementKeys.actionFor(PadKey.Up, KeyPhase.Repeat, isTextFieldFocused = false))
            .isEqualTo(ElementKeyAction.OpenInterface)
        assertThat(ElementKeys.actionFor(PadKey.Up, KeyPhase.Repeat, isTextFieldFocused = true))
            .isEqualTo(ElementKeyAction.OpenInterface)
    }

    @Test
    fun `Given Down is held When the key repeats Then elements keep advancing`() {
        assertThat(ElementKeys.actionFor(PadKey.Down, KeyPhase.Repeat, isTextFieldFocused = false))
            .isEqualTo(ElementKeyAction.Send(ElementKey.Next))
    }

    @Test
    fun `Given OK is held When the key repeats Then the element is activated only once`() {
        assertThat(ElementKeys.actionFor(PadKey.Ok, KeyPhase.Repeat, isTextFieldFocused = false))
            .isEqualTo(ElementKeyAction.Ignore)
    }

    @Test
    fun `Given a key already handled When it is released Then nothing more happens`() {
        PadKey.entries.forEach { key ->
            assertThat(ElementKeys.actionFor(key, KeyPhase.Up, isTextFieldFocused = false)).isEqualTo(ElementKeyAction.Ignore)
        }
    }
}

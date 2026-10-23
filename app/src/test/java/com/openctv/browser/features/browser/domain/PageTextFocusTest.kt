package com.openctv.browser.features.browser.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageTextFocusTest {
    private val focus = PageTextFocus()

    @Test
    fun `Given a page shown and the engine reporting an editor When asked Then a text field is focused`() {
        assertThat(focus.isTextFieldFocused(isEditorFocusedInEngine = true, isPageDisplayed = true)).isTrue()
    }

    @Test
    fun `Given the engine reporting no editor When asked Then no text field is focused`() {
        assertThat(focus.isTextFieldFocused(isEditorFocusedInEngine = false, isPageDisplayed = true)).isFalse()
    }

    @Test
    fun `Given an error or warning on screen When the engine still reports an editor Then no text field is focused`() {
        assertThat(focus.isTextFieldFocused(isEditorFocusedInEngine = true, isPageDisplayed = false)).isFalse()
    }

    @Test
    fun `Given a page covered then shown again When the engine reports an editor Then it is ignored until the user acts on the page`() {
        focus.onPageCovered()
        assertThat(focus.isTextFieldFocused(isEditorFocusedInEngine = true, isPageDisplayed = true)).isFalse()

        focus.onPageInteraction()
        assertThat(focus.isTextFieldFocused(isEditorFocusedInEngine = true, isPageDisplayed = true)).isTrue()
    }

    @Test
    fun `Given a field focused and the keyboard visible When asked Then the keyboard is shown`() {
        assertThat(focus.isKeyboardShown(isEditorFocusedInEngine = true, isKeyboardShownBySystem = true, isPageDisplayed = true)).isTrue()
    }

    @Test
    fun `Given a field focused but the keyboard closed by Back When asked Then the keyboard is not shown`() {
        assertThat(focus.isKeyboardShown(isEditorFocusedInEngine = true, isKeyboardShownBySystem = false, isPageDisplayed = true)).isFalse()
    }

    @Test
    fun `Given a keyboard visible but no field focused or a stale engine state When asked Then the keyboard is not shown`() {
        assertThat(focus.isKeyboardShown(isEditorFocusedInEngine = false, isKeyboardShownBySystem = true, isPageDisplayed = true)).isFalse()
        focus.onPageCovered()
        assertThat(focus.isKeyboardShown(isEditorFocusedInEngine = true, isKeyboardShownBySystem = true, isPageDisplayed = true)).isFalse()
    }
}

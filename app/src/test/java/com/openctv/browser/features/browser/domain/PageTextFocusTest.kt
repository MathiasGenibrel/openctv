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
}

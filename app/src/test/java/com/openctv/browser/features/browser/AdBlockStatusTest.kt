package com.openctv.browser.features.browser

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.features.browser.domain.AdBlockStatus
import org.junit.Test

class AdBlockStatusTest {

    @Test
    fun `Given no request blocked When the menu words the count Then it is singular like in French`() {
        assertThat(AdBlockStatus.Active(0).pluralQuantity).isEqualTo(1)
    }

    @Test
    fun `Given one request blocked When the menu words the count Then it is singular`() {
        assertThat(AdBlockStatus.Active(1).pluralQuantity).isEqualTo(1)
    }

    @Test
    fun `Given twelve requests blocked When the menu words the count Then it is plural`() {
        assertThat(AdBlockStatus.Active(12).pluralQuantity).isEqualTo(12)
    }
}

package com.openctv.browser.features.adblock

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.features.adblock.data.CookieBannerSelectionSource
import com.openctv.browser.features.adblock.data.CookieListParser
import com.openctv.browser.features.adblock.domain.DefaultAdBlockEngine
import com.openctv.browser.features.settings.domain.Settings
import com.openctv.browser.features.settings.domain.SettingsContract
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class CookieBannerSelectionSourceTest {

    private val engine = DefaultAdBlockEngine().apply {
        val list = CookieListParser.parse("##.cookie-banner".lineSequence())
        updateCookieFilters(list.network, list.cosmetic)
    }

    private fun sourceWith(settings: Settings): CookieBannerSelectionSource {
        val contract = mockk<SettingsContract> { every { current } returns settings }
        return CookieBannerSelectionSource(engine, contract)
    }

    private val page = "https://20minutes.fr/"

    @Test
    fun `Given the blocker and cookie banners enabled When a page opens Then its banners are hidden`() {
        val selection = sourceWith(Settings()).selectionFor(page)

        assertThat(selection.generic).containsExactly(".cookie-banner")
    }

    @Test
    fun `Given the cookie banners setting off When a page opens Then nothing is hidden`() {
        val selection = sourceWith(Settings(isCookieBannerBlockEnabled = false)).selectionFor(page)

        assertThat(selection.isEmpty).isTrue()
    }

    @Test
    fun `Given the ad blocker off When a page opens Then no cookie rule applies even if the cookie setting is on`() {
        val selection = sourceWith(Settings(isAdBlockEnabled = false, isCookieBannerBlockEnabled = true)).selectionFor(page)

        assertThat(selection.isEmpty).isTrue()
    }

    @Test
    fun `Given a site where the blocker is disabled When it opens Then nothing is hidden on it`() {
        engine.updateDisabledSites(listOf("20minutes.fr"))

        val selection = sourceWith(Settings()).selectionFor(page)

        assertThat(selection.isEmpty).isTrue()
    }
}

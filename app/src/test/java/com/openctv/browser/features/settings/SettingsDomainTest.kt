package com.openctv.browser.features.settings

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.features.settings.domain.CursorSpeed
import com.openctv.browser.features.settings.domain.HudHideDelay
import com.openctv.browser.features.settings.domain.SearchEngine
import com.openctv.browser.features.settings.domain.Settings
import com.openctv.browser.features.settings.domain.StartPage
import org.junit.Test

class SettingsDomainTest {

    @Test
    fun `Given a first launch When reading the settings Then the documented defaults apply`() {
        val settings = Settings()

        assertThat(settings.searchEngine).isEqualTo(SearchEngine.DuckDuckGo)
        assertThat(settings.startPage).isEqualTo(StartPage.Home)
        assertThat(settings.hudHideDelay).isEqualTo(HudHideDelay.Seconds5)
        assertThat(settings.cursorSpeed).isEqualTo(CursorSpeed.Normal)
        assertThat(settings.isAdBlockEnabled).isTrue()
        assertThat(settings.lastPage).isNull()
    }

    @Test
    fun `Given every search engine When listing them Then only DuckDuckGo Qwant and Brave are tracking free`() {
        val trackingFree = SearchEngine.entries.filter { it.isTrackingFree }

        assertThat(trackingFree)
            .containsExactly(SearchEngine.DuckDuckGo, SearchEngine.Qwant, SearchEngine.BraveSearch)
    }

    @Test
    fun `Given a query with spaces and special characters When building the search URL Then it is encoded`() {
        val url = SearchEngine.DuckDuckGo.searchUrl("café & thé")

        assertThat(url).isEqualTo("https://duckduckgo.com/?q=caf%C3%A9+%26+th%C3%A9")
    }

    @Test
    fun `Given each engine When searching Then the query lands in the engine results page`() {
        val urls = SearchEngine.entries.associateWith { it.searchUrl("arte") }

        assertThat(urls.getValue(SearchEngine.Google)).isEqualTo("https://www.google.com/search?q=arte")
        assertThat(urls.getValue(SearchEngine.Bing)).isEqualTo("https://www.bing.com/search?q=arte")
        assertThat(urls.getValue(SearchEngine.Qwant)).isEqualTo("https://www.qwant.com/?q=arte")
        assertThat(urls.getValue(SearchEngine.Ecosia)).isEqualTo("https://www.ecosia.org/search?q=arte")
        assertThat(urls.getValue(SearchEngine.BraveSearch)).isEqualTo("https://search.brave.com/search?q=arte")
    }

    @Test
    fun `Given the start page is a precise site When launching Then that site opens directly`() {
        val settings = Settings(startPage = StartPage.Site("https://arte.tv"))

        assertThat(settings.launchUrl()).isEqualTo("https://arte.tv")
    }

    @Test
    fun `Given resume last page and no page visited When launching Then the home screen shows`() {
        val settings = Settings(startPage = StartPage.LastPage, lastPage = null)

        assertThat(settings.launchUrl()).isNull()
    }

    @Test
    fun `Given resume last page and a visited page When launching Then that page reopens`() {
        val settings = Settings(startPage = StartPage.LastPage, lastPage = "https://arte.tv/fr")

        assertThat(settings.launchUrl()).isEqualTo("https://arte.tv/fr")
    }

    @Test
    fun `Given a last page but the home start page When launching Then the home screen shows`() {
        val settings = Settings(startPage = StartPage.Home, lastPage = "https://arte.tv")

        assertThat(settings.launchUrl()).isNull()
    }

    @Test
    fun `Given a bare domain typed by the user When choosing a site Then https is added`() {
        assertThat(StartPage.fromInput("  arte.tv ")).isEqualTo(StartPage.Site("https://arte.tv"))
        assertThat(StartPage.fromInput("http://exemple.org/a")).isEqualTo(StartPage.Site("http://exemple.org/a"))
    }

    @Test
    fun `Given input that is not a web address When choosing a site Then it is refused`() {
        assertThat(StartPage.fromInput("")).isNull()
        assertThat(StartPage.fromInput("recherche avec espaces")).isNull()
        assertThat(StartPage.fromInput("javascript://alert(1)")).isNull()
        assertThat(StartPage.fromInput("file:///sdcard/x.html")).isNull()
        assertThat(StartPage.fromInput("intent://scan/#Intent;end")).isNull()
    }

    @Test
    fun `Given an unknown stored id When decoding an option Then the default is used`() {
        assertThat(SearchEngine.fromId("altavista")).isEqualTo(SearchEngine.DuckDuckGo)
        assertThat(HudHideDelay.fromId(null)).isEqualTo(HudHideDelay.Seconds5)
        assertThat(CursorSpeed.fromId("warp")).isEqualTo(CursorSpeed.Normal)
        assertThat(HudHideDelay.Never.delay).isNull()
    }
}

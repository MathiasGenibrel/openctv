package com.openctv.browser.features.settings.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.openctv.browser.core.zoom.PageZoom
import com.openctv.browser.features.settings.domain.CursorSpeed
import com.openctv.browser.features.settings.domain.HudHideDelay
import com.openctv.browser.features.settings.domain.SearchEngine
import com.openctv.browser.features.settings.domain.Settings
import com.openctv.browser.features.settings.domain.StartPage

/** Conversion [Settings] <-> préférences DataStore. Toute valeur absente ou inconnue retombe sur le défaut. */
internal object SettingsPreferencesCodec {
    private val SearchEngineKey = stringPreferencesKey("search_engine")
    private val StartPageKey = stringPreferencesKey("start_page")
    private val HudHideDelayKey = stringPreferencesKey("hud_hide_delay")
    private val CursorSpeedKey = stringPreferencesKey("cursor_speed")
    private val PageZoomKey = stringPreferencesKey("page_zoom")
    private val AdBlockKey = booleanPreferencesKey("ad_block_enabled")
    private val LastPageKey = stringPreferencesKey("last_page")

    private const val HomeToken = "home"
    private const val LastPageToken = "last"
    private const val SitePrefix = "site:"

    fun decode(preferences: Preferences): Settings {
        val defaults = Settings()
        return Settings(
            searchEngine = SearchEngine.fromId(preferences[SearchEngineKey]),
            startPage = decodeStartPage(preferences[StartPageKey]),
            hudHideDelay = HudHideDelay.fromId(preferences[HudHideDelayKey]),
            cursorSpeed = CursorSpeed.fromId(preferences[CursorSpeedKey]),
            pageZoom = PageZoom.fromId(preferences[PageZoomKey]) ?: defaults.pageZoom,
            isAdBlockEnabled = preferences[AdBlockKey] ?: defaults.isAdBlockEnabled,
            lastPage = preferences[LastPageKey],
        )
    }

    fun encode(settings: Settings, into: MutablePreferences) {
        into[SearchEngineKey] = settings.searchEngine.id
        into[StartPageKey] = encodeStartPage(settings.startPage)
        into[HudHideDelayKey] = settings.hudHideDelay.id
        into[CursorSpeedKey] = settings.cursorSpeed.id
        into[PageZoomKey] = settings.pageZoom.id
        into[AdBlockKey] = settings.isAdBlockEnabled
        val lastPage = settings.lastPage
        if (lastPage == null) into.remove(LastPageKey) else into[LastPageKey] = lastPage
    }

    private fun encodeStartPage(page: StartPage): String = when (page) {
        StartPage.Home -> HomeToken
        StartPage.LastPage -> LastPageToken
        is StartPage.Site -> SitePrefix + page.url
    }

    private fun decodeStartPage(raw: String?): StartPage = when {
        raw == LastPageToken -> StartPage.LastPage
        raw != null && raw.startsWith(SitePrefix) ->
            StartPage.fromInput(raw.removePrefix(SitePrefix)) ?: StartPage.Default
        else -> StartPage.Default
    }
}

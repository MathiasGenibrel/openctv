package com.openctv.browser.features.settings.ui

import androidx.annotation.StringRes
import com.openctv.browser.R
import com.openctv.browser.features.settings.domain.CursorSpeed
import com.openctv.browser.features.settings.domain.HudHideDelay
import com.openctv.browser.features.settings.domain.StartPage

@StringRes
internal fun HudHideDelay.labelRes(): Int = when (this) {
    HudHideDelay.Seconds3 -> R.string.settings_hud_3
    HudHideDelay.Seconds5 -> R.string.settings_hud_5
    HudHideDelay.Seconds10 -> R.string.settings_hud_10
    HudHideDelay.Never -> R.string.settings_hud_never
}

@StringRes
internal fun CursorSpeed.labelRes(): Int = when (this) {
    CursorSpeed.Slow -> R.string.settings_cursor_slow
    CursorSpeed.Normal -> R.string.settings_cursor_normal
    CursorSpeed.Fast -> R.string.settings_cursor_fast
}

@StringRes
internal fun StartPage.labelRes(): Int = when (this) {
    StartPage.Home -> R.string.settings_start_home
    StartPage.LastPage -> R.string.settings_start_last
    is StartPage.Site -> R.string.settings_start_site
}

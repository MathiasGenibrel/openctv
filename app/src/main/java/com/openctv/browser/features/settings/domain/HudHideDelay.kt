package com.openctv.browser.features.settings.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Délai avant masquage de l'interface (HUD) en navigation. [delay] vaut `null` pour « Jamais ». */
enum class HudHideDelay(val id: String, val delay: Duration?) {
    Seconds3("3s", 3.seconds),
    Seconds5("5s", 5.seconds),
    Seconds10("10s", 10.seconds),
    Never("never", null);

    companion object {
        val Default = Seconds5

        fun fromId(id: String?): HudHideDelay = entries.firstOrNull { it.id == id } ?: Default
    }
}

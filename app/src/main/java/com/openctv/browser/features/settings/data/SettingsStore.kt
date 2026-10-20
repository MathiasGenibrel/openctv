package com.openctv.browser.features.settings.data

import com.openctv.browser.features.settings.domain.Settings
import kotlinx.coroutines.flow.Flow

/** Stockage local des préférences (DataStore en production, faux en test). */
interface SettingsStore {
    /** Émet les préférences courantes, puis à chaque changement. */
    val settings: Flow<Settings>

    /** Applique [transform] à la valeur stockée de façon atomique. */
    suspend fun update(transform: (Settings) -> Settings)
}

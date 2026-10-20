package com.openctv.browser.features.settings

import com.openctv.browser.features.settings.data.SettingsStore
import com.openctv.browser.features.settings.domain.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsStore(initial: Settings = Settings()) : SettingsStore {
    val current = MutableStateFlow(initial)
    var failure: Exception? = null

    override val settings: Flow<Settings> = current

    override suspend fun update(transform: (Settings) -> Settings) {
        failure?.let { throw it }
        current.value = transform(current.value)
    }
}

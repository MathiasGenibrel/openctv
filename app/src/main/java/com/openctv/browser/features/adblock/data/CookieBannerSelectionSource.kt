package com.openctv.browser.features.adblock.data

import com.openctv.browser.core.webview.CosmeticSelection
import com.openctv.browser.core.webview.CosmeticSelectionSource
import com.openctv.browser.features.adblock.domain.AdBlockEngine
import com.openctv.browser.features.settings.domain.SettingsContract

/**
 * Règles de masquage des bandeaux de cookies pour la page principale. Rien si le bloqueur est coupé globalement,
 * si le réglage « Bandeaux de cookies » l'est, ou si le blocage est désactivé pour le site (décision du moteur).
 * Lectures en mémoire uniquement.
 */
class CookieBannerSelectionSource(
    private val engine: AdBlockEngine,
    private val settings: SettingsContract,
) : CosmeticSelectionSource {

    override fun selectionFor(pageUrl: String): CosmeticSelection {
        val current = settings.current
        val isHidingEnabled = current.isAdBlockEnabled && current.isCookieBannerBlockEnabled
        if (!isHidingEnabled) return CosmeticSelection.EMPTY
        return engine.cosmeticSelectionFor(pageUrl)
    }
}

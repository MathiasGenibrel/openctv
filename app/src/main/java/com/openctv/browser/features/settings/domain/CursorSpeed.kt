package com.openctv.browser.features.settings.domain

/** Vitesse du curseur virtuel ; [factor] multiplie la vitesse de base (1 = normale). */
enum class CursorSpeed(val id: String, val factor: Float) {
    Slow("slow", 0.6f),
    Normal("normal", 1f),
    Fast("fast", 1.6f);

    companion object {
        val Default = Normal

        fun fromId(id: String?): CursorSpeed = entries.firstOrNull { it.id == id } ?: Default
    }
}

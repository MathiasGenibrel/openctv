package com.openctv.browser.features.adblock.domain

import java.time.Instant

data class FilterListsState(
    /** `null` : aucune liste n'a jamais été téléchargée (le bloqueur ne bloque alors rien). */
    val lastUpdated: Instant? = null,
    val isUpdating: Boolean = false,
    /** Message de la dernière mise à jour échouée, effacé par la suivante. */
    val error: String? = null,
)

package com.openctv.browser.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner

/** Affiche [destination] dans sa propre portée de ViewModels (cf. [ScopedViewModels]). */
@Composable
fun DestinationHost(destination: Destination, content: @Composable (Destination) -> Unit) {
    ScopedViewModels(destination) { content(destination) }
}

/**
 * Portée de ViewModels liée à [key] : les `viewModel { … }` de [content] sont créés à l'entrée et vidés
 * quand [content] quitte la composition ; ils survivent à une recréation de l'activité. Sans cette
 * portée, un écran rouvert (même URL, même saisie) retrouverait le ViewModel de la visite précédente,
 * retenu par l'activité. Sert aux destinations comme aux surcouches (saisie).
 */
@Composable
fun ScopedViewModels(key: Any, content: @Composable () -> Unit) {
    key(key) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides rememberViewModelStoreOwner()) {
            content()
        }
    }
}

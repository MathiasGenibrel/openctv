package com.openctv.browser.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner

/**
 * Affiche [destination] avec ses propres ViewModels : créés à l'entrée, vidés quand on la quitte.
 * Ils survivent à une recréation de l'activité. Sans cette portée, un écran rouvert (même URL)
 * retrouverait le ViewModel de la visite précédente, retenu par l'activité.
 */
@Composable
fun DestinationHost(destination: Destination, content: @Composable (Destination) -> Unit) {
    key(destination) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides rememberViewModelStoreOwner()) {
            content(destination)
        }
    }
}

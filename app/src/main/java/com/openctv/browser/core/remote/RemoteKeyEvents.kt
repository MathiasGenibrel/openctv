package com.openctv.browser.core.remote

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type

/**
 * Seule traduction d'une touche Compose vers [RemoteKeyMap], pour l'accueil comme pour le navigateur : un appui (ou
 * une répétition) d'une touche de la table est confié à [handler] avec son action ; un relâchement ou une touche
 * hors table renvoie `false` et poursuit son chemin. Les décisions restent au ViewModel ([handler]).
 */
fun KeyEvent.dispatchRemoteAction(handler: (action: RemoteAction, isRepeat: Boolean) -> Boolean): Boolean {
    val isKeyDown = type == KeyEventType.KeyDown
    val action = RemoteKeyMap.actionFor(nativeKeyEvent.keyCode)
    if (!isKeyDown || action == null) return false
    return handler(action, nativeKeyEvent.repeatCount > 0)
}

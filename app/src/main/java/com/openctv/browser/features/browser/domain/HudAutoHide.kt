package com.openctv.browser.features.browser.domain

import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Minuteur d'inactivité de l'interface : [isIdle] passe à `true` quand [onUserActivity] n'a pas été appelé
 * pendant le délai réglé. Une seule coroutine en attente, relancée à chaque activité (aucun travail
 * périodique). Les exceptions au masquage (surcouche, chargement, erreur) sont décidées par [browserHudOf].
 *
 * @param hideDelay lu à chaque relance : un changement de réglage s'applique à l'activité suivante,
 * `null` = jamais.
 */
class HudAutoHide(private val scope: CoroutineScope, private val hideDelay: () -> Duration?) {
    private val _isIdle = MutableStateFlow(false)
    val isIdle: StateFlow<Boolean> = _isIdle.asStateFlow()
    private var timer: Job? = null

    /** Une touche ou un changement d'état vient de se produire : l'interface revient et le minuteur repart. */
    fun onUserActivity() {
        _isIdle.value = false
        timer?.cancel()
        val delay = hideDelay() ?: return
        timer = scope.launch {
            delay(delay)
            _isIdle.value = true
        }
    }
}

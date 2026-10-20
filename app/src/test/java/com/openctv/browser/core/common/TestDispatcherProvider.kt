package com.openctv.browser.core.common

import kotlinx.coroutines.CoroutineDispatcher

/** Dispatchers de test : tout s'exécute sur le dispatcher fourni (typiquement lié au `testScheduler`). */
class TestDispatcherProvider(dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val main = dispatcher
    override val io = dispatcher
    override val default = dispatcher
}

package com.openctv.browser.features.adblock.domain

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Compteur de requêtes bloquées par site, en mémoire. [increment] est appelé depuis les threads de
 * `shouldInterceptRequest` : un compteur atomique et un tick, sans copie de table. Les lecteurs
 * relisent le compteur à chaque tick (conflaté).
 */
class BlockedRequestCounter {
    private val counts = ConcurrentHashMap<String, AtomicInteger>()
    private val ticks = MutableStateFlow(0L)

    fun increment(site: String) {
        val key = HostUtils.siteKeyOf(site) ?: return
        counts.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet()
        ticks.update { it + 1 }
    }

    fun countOf(site: String): Int {
        val key = HostUtils.siteKeyOf(site) ?: return 0
        return counts[key]?.get() ?: 0
    }

    fun observe(site: String): Flow<Int> = ticks.map { countOf(site) }.distinctUntilChanged()
}

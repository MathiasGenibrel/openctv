package com.openctv.browser.features.settings

import com.openctv.browser.core.common.DispatcherProvider
import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.webview.WebDataContract
import com.openctv.browser.features.history.domain.HistoryContract
import com.openctv.browser.features.history.domain.HistoryEntry
import com.openctv.browser.features.search.domain.RecentSearchesContract
import com.openctv.browser.features.settings.data.SettingsImpl
import com.openctv.browser.features.settings.domain.Settings
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Historique en mémoire ; [failure] non nul : l'effacement échoue avec ce message. */
class FakeHistory(vararg urls: String) : HistoryContract {
    val entries = MutableStateFlow(urls.mapIndexed { index, url -> HistoryEntry(index.toLong(), url, "", Instant.EPOCH) })
    var failure: String? = null

    override fun getEntries(): Flow<Resource<List<HistoryEntry>>> = entries.map { Resource.Success(it) }

    override suspend fun remove(id: Long): Resource<Unit> = Resource.Success(Unit)

    override suspend fun clear(): Resource<Unit> {
        failure?.let { return Resource.Error(it) }
        entries.value = emptyList()
        return Resource.Success(Unit)
    }
}

class FakeRecentSearches(vararg queries: String) : RecentSearchesContract {
    val queries = MutableStateFlow(queries.toList())
    var failure: String? = null

    override fun getRecents(): Flow<Resource<List<String>>> = queries.map { Resource.Success(it) }

    override suspend fun record(query: String): Resource<Unit> = Resource.Success(Unit)

    override suspend fun clear(): Resource<Unit> {
        failure?.let { return Resource.Error(it) }
        queries.value = emptyList()
        return Resource.Success(Unit)
    }
}

/**
 * Données du moteur WebView : `true` tant qu'elles sont là, `false` une fois effacées. [siteDataGate] non nul : l'effacement
 * des données de sites attend qu'il soit complété (effacement en cours).
 */
class FakeWebData : WebDataContract {
    var hasSiteData = true
    var hasCache = true
    var siteDataFailure: String? = null
    var cacheFailure: String? = null
    var siteDataGate: CompletableDeferred<Unit>? = null

    override suspend fun clearSiteData(): Resource<Unit> {
        siteDataGate?.await()
        siteDataFailure?.let { return Resource.Error(it) }
        hasSiteData = false
        return Resource.Success(Unit)
    }

    override suspend fun clearCache(): Resource<Unit> {
        cacheFailure?.let { return Resource.Error(it) }
        hasCache = false
        return Resource.Success(Unit)
    }
}

/** Vrais [SettingsImpl] sur un stockage mémoire : la dernière page est réellement oubliée. */
fun settingsImpl(
    scope: CoroutineScope,
    dispatchers: DispatcherProvider,
    initial: Settings = Settings(),
) = SettingsImpl(FakeSettingsStore(initial), dispatchers, scope)

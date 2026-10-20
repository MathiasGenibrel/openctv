package com.openctv.browser.features.adblock

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.common.TestDispatcherProvider
import com.openctv.browser.features.adblock.data.AdBlockImpl
import com.openctv.browser.features.adblock.data.DisabledSitesStore
import com.openctv.browser.features.adblock.data.FileFilterListCache
import com.openctv.browser.features.adblock.data.FilterListFetcher
import com.openctv.browser.features.adblock.domain.BlockedRequestCounter
import com.openctv.browser.features.adblock.domain.DefaultAdBlockEngine
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AdBlockImplTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val now = Instant.parse("2026-10-20T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val engine = DefaultAdBlockEngine()
    private val counter = BlockedRequestCounter()
    private val sitesStore = InMemorySitesStore()
    private val cacheDir by lazy { folder.newFolder() }
    private val cache by lazy { FileFilterListCache(cacheDir, clock) }

    private var downloads = 0
    private var download: () -> String = { error("réseau non prévu") }
    private val fetcher = FilterListFetcher {
        downloads++
        ByteArrayInputStream(download().toByteArray())
    }

    private val appScopes = mutableListOf<CoroutineScope>()

    @After
    fun stopApp() = appScopes.forEach { it.cancel() }

    private fun TestScope.adBlock(): AdBlockImpl {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appScope = CoroutineScope(SupervisorJob() + dispatcher).also(appScopes::add)
        return AdBlockImpl(
            engine = engine,
            fetcher = fetcher,
            cache = cache,
            sitesStore = sitesStore,
            counter = counter,
            dispatchers = TestDispatcherProvider(dispatcher),
            scope = appScope,
            clock = clock,
        )
    }

    private fun cachedList(content: String, age: Duration) {
        File(cacheDir, "filters.txt").apply {
            writeText(content)
            setLastModified(now.minus(age).toEpochMilli())
        }
    }

    private fun isBlocked(url: String, page: String = "https://site.org/") = engine.shouldBlock(url, page)

    @Test
    fun `Given a fresh cache When the app starts Then the cached list blocks ads without any download`() = runTest {
        // Given
        cachedList("||ads.example.com^\n", age = Duration.ofDays(1))
        val adBlock = adBlock()

        // When
        adBlock.start()
        advanceUntilIdle()

        // Then
        assertThat(isBlocked("https://ads.example.com/banner.js")).isTrue()
        assertThat(downloads).isEqualTo(0)
        assertThat(adBlock.filterLists.value.lastUpdated).isEqualTo(now.minus(Duration.ofDays(1)))
    }

    @Test
    fun `Given a stale cache When the app starts Then the cache is active at once and the list is refreshed`() = runTest {
        // Given
        cachedList("||old-ads.example.com^\n", age = Duration.ofDays(10))
        download = { "||new-ads.example.com^\n" }
        val adBlock = adBlock()

        // When
        adBlock.start()
        advanceUntilIdle()

        // Then
        assertThat(downloads).isEqualTo(1)
        assertThat(isBlocked("https://new-ads.example.com/x")).isTrue()
        assertThat(adBlock.filterLists.value.lastUpdated).isEqualTo(now)
    }

    @Test
    fun `Given no list ever downloaded and the TV offline When the app starts Then nothing is blocked and the error is reported`() = runTest {
        // Given
        download = { throw IOException("Unable to resolve host") }
        val adBlock = adBlock()

        // When
        adBlock.start()
        advanceUntilIdle()

        // Then
        assertThat(isBlocked("https://ads.example.com/banner.js")).isFalse()
        assertThat(adBlock.filterLists.value.lastUpdated).isNull()
        assertThat(adBlock.filterLists.value.error).contains("Unable to resolve host")
        assertThat(adBlock.filterLists.value.isUpdating).isFalse()
    }

    @Test
    fun `Given an active list When the download fails Then the old list stays active and Resource Error is returned`() = runTest {
        // Given
        cachedList("||ads.example.com^\n", age = Duration.ofDays(10))
        val adBlock = adBlock()
        adBlock.start()
        advanceUntilIdle()
        download = { throw IOException("HTTP 503") }

        // When
        val result = adBlock.refreshNow()

        // Then
        assertThat(result).isEqualTo(Resource.Error("HTTP 503"))
        assertThat(isBlocked("https://ads.example.com/banner.js")).isTrue()
        assertThat(cache.readLines { it.toList() }).containsExactly("||ads.example.com^")
    }

    @Test
    fun `Given an active list When the server answers with a page that is not a list Then the old list is kept`() = runTest {
        // Given
        cachedList("||ads.example.com^\n", age = Duration.ofDays(10))
        val adBlock = adBlock()
        adBlock.start()
        advanceUntilIdle()
        download = { "<html>Connectez-vous au Wi-Fi</html>" }

        // When
        val result = adBlock.refreshNow()

        // Then
        assertThat(result).isInstanceOf(Resource.Error::class.java)
        assertThat(isBlocked("https://ads.example.com/banner.js")).isTrue()
    }

    @Test
    fun `Given a site where blocking is disabled When the page loads Then nothing is blocked there and the choice is persisted`() = runTest {
        // Given
        cachedList("||ads.example.com^\n", age = Duration.ofDays(1))
        val adBlock = adBlock()
        adBlock.start()
        advanceUntilIdle()

        // When
        val result = adBlock.setEnabledForSite("https://www.arte.tv/fr/", isEnabled = false)
        advanceUntilIdle()

        // Then
        assertThat(result).isEqualTo(Resource.Success(Unit))
        assertThat(sitesStore.sites.value).containsExactly("www.arte.tv")
        assertThat(isBlocked("https://ads.example.com/x", page = "https://www.arte.tv/fr/")).isFalse()
        assertThat(isBlocked("https://ads.example.com/x", page = "https://site.org/")).isTrue()
        assertThat(adBlock.isEnabledForSite("www.arte.tv").first()).isFalse()
        assertThat(adBlock.isEnabledForSite("site.org").first()).isTrue()
    }

    @Test
    fun `Given a previously disabled site When the app restarts Then the stored choice is applied to the engine`() = runTest {
        // Given
        cachedList("||ads.example.com^\n", age = Duration.ofDays(1))
        sitesStore.sites.value = setOf("arte.tv")
        val adBlock = adBlock()

        // When
        adBlock.start()
        advanceUntilIdle()

        // Then
        assertThat(isBlocked("https://ads.example.com/x", page = "https://arte.tv/")).isFalse()
    }

    @Test
    fun `Given a disabled site When blocking is re-enabled Then ads are blocked again`() = runTest {
        // Given
        cachedList("||ads.example.com^\n", age = Duration.ofDays(1))
        val adBlock = adBlock()
        adBlock.start()
        adBlock.setEnabledForSite("arte.tv", isEnabled = false)
        advanceUntilIdle()

        // When
        adBlock.setEnabledForSite("https://arte.tv/", isEnabled = true)
        advanceUntilIdle()

        // Then
        assertThat(isBlocked("https://ads.example.com/x", page = "https://arte.tv/")).isTrue()
        assertThat(adBlock.isEnabledForSite("arte.tv").first()).isTrue()
    }

    @Test
    fun `Given blocked requests on two sites When the counter is read Then each site has its own count`() = runTest {
        // Given
        val adBlock = adBlock()

        // When
        counter.increment("https://arte.tv/fr/")
        counter.increment("https://arte.tv/de/")
        counter.increment("https://wikipedia.org/")

        // Then
        assertThat(adBlock.blockedCount("arte.tv").first()).isEqualTo(2)
        assertThat(adBlock.blockedCount("https://wikipedia.org").first()).isEqualTo(1)
        assertThat(adBlock.blockedCount("example.com").first()).isEqualTo(0)
    }

    @Test
    fun `Given a download cut off midway When the list is staged Then no partial file is left and the old list stays active`() = runTest {
        // Given
        cachedList("||ads.example.com^\n", age = Duration.ofDays(10))
        val adBlock = adBlock()
        adBlock.start()
        advanceUntilIdle()
        val brokenStream = object : java.io.InputStream() {
            private var sent = false
            override fun read(): Int = if (!sent) { sent = true; '|'.code } else throw IOException("Connexion perdue")
        }

        // When
        val result = runCatching { cache.stage(brokenStream) }

        // Then
        assertThat(result.exceptionOrNull()).isInstanceOf(IOException::class.java)
        assertThat(cacheDir.list()).asList().containsExactly("filters.txt")
        assertThat(isBlocked("https://ads.example.com/banner.js")).isTrue()
        assertThat(adBlock.filterLists.value.lastUpdated).isNotNull()
    }

    @Test
    fun `Given a page without a valid host When the menu reads its state Then blocking is shown enabled with no blocked request`() = runTest {
        // Given
        val adBlock = adBlock()

        // When
        val isEnabled = adBlock.isEnabledForSite("about:blank").first()
        val count = adBlock.blockedCount("about:blank").first()

        // Then
        assertThat(isEnabled).isTrue()
        assertThat(count).isEqualTo(0)
    }

    private class InMemorySitesStore : DisabledSitesStore {
        override val sites = MutableStateFlow<Set<String>>(emptySet())
        override suspend fun update(transform: (Set<String>) -> Set<String>) {
            sites.value = transform(sites.value)
        }
    }
}

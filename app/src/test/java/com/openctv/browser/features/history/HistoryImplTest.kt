package com.openctv.browser.features.history

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.common.TestDispatcherProvider
import com.openctv.browser.features.history.data.HistoryDao
import com.openctv.browser.features.history.data.HistoryEntryEntity
import com.openctv.browser.features.history.data.HistoryImpl
import com.openctv.browser.features.history.domain.HistoryEntry
import com.openctv.browser.features.history.domain.HistoryPolicy
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HistoryImplTest {

    private val start = Instant.parse("2026-10-21T10:00:00Z")
    private val clock = MutableClock(start)
    private val dao = FakeHistoryDao()

    private fun TestScope.history() = HistoryImpl(
        dao = dao,
        dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
        clock = clock,
    )

    private suspend fun HistoryImpl.entries(): List<HistoryEntry> =
        (getEntries().first { it is Resource.Success } as Resource.Success).data

    @Test
    fun `Given a web page is displayed When it is recorded Then the history lists it with its title and time`() = runTest {
        val history = history()

        history.recordVisit("https://www.arte.tv/fr/", "ARTE")

        val entry = history.entries().single()
        assertThat(entry.url).isEqualTo("https://www.arte.tv/fr/")
        assertThat(entry.title).isEqualTo("ARTE")
        assertThat(entry.visitedAt).isEqualTo(start)
    }

    @Test
    fun `Given a page not shown as a web page When it is recorded Then nothing is stored`() = runTest {
        val history = history()

        history.recordVisit("about:blank", "")
        history.recordVisit("data:text/html,error", "Webpage not available")

        assertThat(history.entries()).isEmpty()
    }

    @Test
    fun `Given the same page is recorded twice in a row When its title arrives late Then a single entry holds the title`() = runTest {
        val history = history()

        history.recordVisit("https://arte.tv/", "")
        clock.advance(Duration.ofSeconds(2))
        history.recordVisit("https://arte.tv/", "ARTE")

        val entry = history.entries().single()
        assertThat(entry.title).isEqualTo("ARTE")
        assertThat(entry.visitedAt).isEqualTo(start.plusSeconds(2))
    }

    @Test
    fun `Given a known title When the same page is merged without title Then the title is kept`() = runTest {
        val history = history()

        history.recordVisit("https://arte.tv/", "ARTE")
        history.recordVisit("https://arte.tv/", "")

        assertThat(history.entries().single().title).isEqualTo("ARTE")
    }

    @Test
    fun `Given the user went elsewhere in between When the first page is visited again Then it is a new entry`() = runTest {
        val history = history()

        history.recordVisit("https://arte.tv/", "ARTE")
        clock.advance(Duration.ofMinutes(1))
        history.recordVisit("https://france.tv/", "France TV")
        clock.advance(Duration.ofMinutes(1))
        history.recordVisit("https://arte.tv/", "ARTE")

        assertThat(history.entries().map { it.url })
            .containsExactly("https://arte.tv/", "https://france.tv/", "https://arte.tv/").inOrder()
    }

    @Test
    fun `Given more than 1000 visits When recording Then only the most recent 1000 are kept`() = runTest {
        val history = history()

        repeat(HistoryPolicy.MAX_ENTRIES + 5) { index ->
            clock.advance(Duration.ofSeconds(1))
            history.recordVisit("https://site$index.example/", "Site $index")
        }

        val entries = history.entries()
        assertThat(entries).hasSize(HistoryPolicy.MAX_ENTRIES)
        assertThat(entries.first().url).isEqualTo("https://site${HistoryPolicy.MAX_ENTRIES + 4}.example/")
        assertThat(entries.none { it.url == "https://site0.example/" }).isTrue()
    }

    @Test
    fun `Given a visit older than 90 days When recording a new page Then the old visit is dropped`() = runTest {
        val history = history()
        history.recordVisit("https://old.example/", "Old")

        clock.advance(Duration.ofDays(91))
        history.recordVisit("https://new.example/", "New")

        assertThat(history.entries().map { it.url }).containsExactly("https://new.example/")
    }

    @Test
    fun `Given a visit older than 90 days When the history screen opens Then it is not listed`() = runTest {
        val history = history()
        history.recordVisit("https://old.example/", "Old")

        clock.advance(Duration.ofDays(120))

        assertThat(history.entries()).isEmpty()
    }

    @Test
    fun `Given entries When the user removes one Then only that one goes`() = runTest {
        val history = history()
        history.recordVisit("https://arte.tv/", "ARTE")
        history.recordVisit("https://france.tv/", "France TV")
        val arte = history.entries().first { it.url == "https://arte.tv/" }

        val result = history.remove(arte.id)

        assertThat(result).isEqualTo(Resource.Success(Unit))
        assertThat(history.entries().map { it.url }).containsExactly("https://france.tv/")
    }

    @Test
    fun `Given entries When the user clears everything Then the history is empty`() = runTest {
        val history = history()
        history.recordVisit("https://arte.tv/", "ARTE")
        history.recordVisit("https://france.tv/", "France TV")

        history.clear()

        assertThat(history.entries()).isEmpty()
    }

    @Test
    fun `Given storage fails When recording a visit Then the error is returned and nothing is thrown`() = runTest {
        dao.failure = IllegalStateException("disk full")

        val result = history().recordVisit("https://arte.tv/", "ARTE")

        assertThat(result).isEqualTo(Resource.Error("disk full"))
    }

    @Test
    fun `Given the history screen observes When listing Then Loading comes first and entries are mapped to domain`() = runTest {
        val history = history()
        history.recordVisit("https://arte.tv/", "ARTE")

        history.getEntries().test {
            assertThat(awaitItem()).isEqualTo(Resource.Loading)
            val success = awaitItem() as Resource.Success
            assertThat(success.data.single().label).isEqualTo("ARTE")
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private class MutableClock(private var now: Instant) : Clock() {
    fun advance(duration: Duration) {
        now += duration
    }

    override fun instant(): Instant = now
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?): Clock = this
}

/** DAO en mémoire qui reproduit les requêtes de [HistoryDao] (tri récent d'abord, élagage, borne). */
private class FakeHistoryDao : HistoryDao {
    private val rows = MutableStateFlow<List<HistoryEntryEntity>>(emptyList())
    private var nextId = 1L
    var failure: Exception? = null

    private fun sorted(list: List<HistoryEntryEntity>) = list.sortedWith(compareByDescending<HistoryEntryEntity> { it.visitedAt }.thenByDescending { it.id })

    override fun observeAll(): Flow<List<HistoryEntryEntity>> = rows.map(::sorted)

    override suspend fun latest(): HistoryEntryEntity? {
        failure?.let { throw it }
        return sorted(rows.value).firstOrNull()
    }

    override suspend fun insert(entity: HistoryEntryEntity) {
        failure?.let { throw it }
        rows.value += entity.copy(id = nextId++)
    }

    override suspend fun update(entity: HistoryEntryEntity) {
        rows.value = rows.value.map { if (it.id == entity.id) entity else it }
    }

    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }

    override suspend fun deleteOlderThan(cutoff: Instant) {
        rows.value = rows.value.filterNot { it.visitedAt < cutoff }
    }

    override suspend fun keepLatest(max: Int) {
        rows.value = sorted(rows.value).take(max)
    }
}

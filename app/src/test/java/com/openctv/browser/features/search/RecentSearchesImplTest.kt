package com.openctv.browser.features.search

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.common.Resource
import com.openctv.browser.features.search.data.RecentSearchesImpl
import com.openctv.browser.features.search.domain.RecentSearchList
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecentSearchesImplTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var repository: RecentSearchesImpl

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "recents.preferences_pb") }
        repository = RecentSearchesImpl(dataStore)
    }

    @After
    fun tearDown() = scope.cancel()

    private suspend fun recents() = (repository.getRecents().first { it !is Resource.Loading } as Resource.Success).data

    @Test
    fun `Given no search yet When listing Then the history is empty after Loading`() = runBlocking {
        repository.getRecents().test {
            assertThat(awaitItem()).isEqualTo(Resource.Loading)
            assertThat(awaitItem()).isEqualTo(Resource.Success(emptyList<String>()))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given searches When recorded Then the most recent comes first and repeats are merged`() = runBlocking {
        repository.record("meteo")
        repository.record("arte documentaire")
        repository.record("Meteo")

        assertThat(recents()).containsExactly("Meteo", "arte documentaire").inOrder()
    }

    @Test
    fun `Given more searches than the limit When recorded Then only the most recent are kept`() = runBlocking {
        (1..RecentSearchList.MaxSize + 5).forEach { repository.record("q$it") }

        val stored = recents()

        assertThat(stored).hasSize(RecentSearchList.MaxSize)
        assertThat(stored.first()).isEqualTo("q${RecentSearchList.MaxSize + 5}")
    }

    @Test
    fun `Given a history When the user clears it Then nothing is left`() = runBlocking {
        repository.record("meteo")

        val result = repository.clear()

        assertThat(result).isEqualTo(Resource.Success(Unit))
        assertThat(recents()).isEmpty()
    }
}

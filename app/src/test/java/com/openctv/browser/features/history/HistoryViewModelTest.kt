package com.openctv.browser.features.history

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.common.Resource
import com.openctv.browser.features.history.domain.DayLabel
import com.openctv.browser.features.history.domain.HistoryContract
import com.openctv.browser.features.history.domain.HistoryEntry
import com.openctv.browser.features.history.domain.HistoryFocus
import com.openctv.browser.features.history.ui.HistoryContent
import com.openctv.browser.features.history.ui.HistoryViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val history = FakeHistory()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): HistoryViewModel =
        HistoryViewModel(history, today = { LocalDate.of(2026, 10, 21) }, zone = { ZoneOffset.UTC })
            .also { it.uiState.onEach { }.launchIn(backgroundScope) }
            .also { runCurrent() }

    private fun entry(id: Long, at: String = "2026-10-21T10:00:00Z") =
        HistoryEntry(id, "https://site$id.example/", "Site $id", Instant.parse(at))

    @Test
    fun `Given visits today and yesterday When the screen opens Then they are listed by day`() = runTest {
        history.entries.value = listOf(entry(3), entry(2, "2026-10-20T10:00:00Z"), entry(1, "2026-10-20T09:00:00Z"))

        val content = viewModel().uiState.value.content as HistoryContent.Entries

        assertThat(content.days.map { it.label }).containsExactly(DayLabel.Today, DayLabel.Yesterday).inOrder()
        assertThat(content.count).isEqualTo(3)
    }

    @Test
    fun `Given no visit When the screen opens Then the empty state is shown and clearing is not offered`() = runTest {
        val viewModel = viewModel()

        assertThat(viewModel.uiState.value.content).isEqualTo(HistoryContent.Empty)
        viewModel.requestClear()
        assertThat(viewModel.uiState.value.isClearConfirmationOpen).isFalse()
    }

    @Test
    fun `Given storage cannot be read When the screen opens Then it says so instead of looking empty`() = runTest {
        history.readFailure = "disk"

        assertThat(viewModel().uiState.value.content).isEqualTo(HistoryContent.Unavailable)
    }

    @Test
    fun `Given three entries When the user removes the middle one Then focus moves to the entry that takes its place`() = runTest {
        history.entries.value = listOf(entry(3), entry(2), entry(1))
        val viewModel = viewModel()

        viewModel.remove(entry(2))

        assertThat(history.entries.value.map { it.id }).containsExactly(3L, 1L).inOrder()
        assertThat(viewModel.focus.value).isEqualTo(HistoryFocus.Entry(1))
    }

    @Test
    fun `Given a single entry When the user removes it Then focus goes to the back button and the empty state shows`() = runTest {
        history.entries.value = listOf(entry(1))
        val viewModel = viewModel()

        viewModel.remove(entry(1))

        assertThat(viewModel.focus.value).isEqualTo(HistoryFocus.BackButton)
        assertThat(viewModel.uiState.value.content).isEqualTo(HistoryContent.Empty)
    }

    @Test
    fun `Given the focus was handed over When the view confirms Then no focus is pending`() = runTest {
        history.entries.value = listOf(entry(2), entry(1))
        val viewModel = viewModel()
        viewModel.remove(entry(2))

        viewModel.onFocusHandled()

        assertThat(viewModel.focus.value).isNull()
    }

    @Test
    fun `Given a removal fails When the user removes an entry Then it stays and the screen reports the failure`() = runTest {
        history.entries.value = listOf(entry(1))
        history.writeFailure = "disk"
        val viewModel = viewModel()

        viewModel.remove(entry(1))

        assertThat(history.entries.value).hasSize(1)
        assertThat(viewModel.uiState.value.isWriteFailed).isTrue()
        assertThat(viewModel.focus.value).isNull()
    }

    @Test
    fun `Given entries When the user asks to clear all Then a confirmation opens and nothing is erased yet`() = runTest {
        history.entries.value = listOf(entry(2), entry(1))
        val viewModel = viewModel()

        viewModel.requestClear()

        assertThat(viewModel.uiState.value.isClearConfirmationOpen).isTrue()
        assertThat(history.entries.value).hasSize(2)
    }

    @Test
    fun `Given the confirmation is open When the user presses Back Then it cancels, keeps the history and refocuses Clear all`() = runTest {
        history.entries.value = listOf(entry(1))
        val viewModel = viewModel()
        viewModel.requestClear()

        val isBackHandled = viewModel.onBack()
        val isSecondBackHandled = viewModel.onBack()

        assertThat(isBackHandled).isTrue()
        assertThat(isSecondBackHandled).isFalse()
        assertThat(viewModel.uiState.value.isClearConfirmationOpen).isFalse()
        assertThat(history.entries.value).hasSize(1)
        assertThat(viewModel.focus.value).isEqualTo(HistoryFocus.ClearAll)
    }

    @Test
    fun `Given the confirmation is open When the user confirms Then the history is erased and the back button is focused`() = runTest {
        history.entries.value = listOf(entry(2), entry(1))
        val viewModel = viewModel()
        viewModel.requestClear()

        viewModel.confirmClear()

        assertThat(history.entries.value).isEmpty()
        assertThat(viewModel.uiState.value.isClearConfirmationOpen).isFalse()
        assertThat(viewModel.uiState.value.content).isEqualTo(HistoryContent.Empty)
        assertThat(viewModel.focus.value).isEqualTo(HistoryFocus.BackButton)
    }

    @Test
    fun `Given clearing fails When the user confirms Then the history stays and focus returns to Clear all`() = runTest {
        history.entries.value = listOf(entry(1))
        history.writeFailure = "disk"
        val viewModel = viewModel()
        viewModel.requestClear()

        viewModel.confirmClear()

        assertThat(history.entries.value).hasSize(1)
        assertThat(viewModel.uiState.value.isWriteFailed).isTrue()
        assertThat(viewModel.focus.value).isEqualTo(HistoryFocus.ClearAll)
    }
}

private class FakeHistory : HistoryContract {
    val entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    var readFailure: String? = null
    var writeFailure: String? = null

    override fun getEntries(): Flow<Resource<List<HistoryEntry>>> =
        entries.map { list -> readFailure?.let { Resource.Error(it) } ?: Resource.Success(list) }

    override suspend fun remove(id: Long): Resource<Unit> {
        writeFailure?.let { return Resource.Error(it) }
        entries.value = entries.value.filterNot { it.id == id }
        return Resource.Success(Unit)
    }

    override suspend fun clear(): Resource<Unit> {
        writeFailure?.let { return Resource.Error(it) }
        entries.value = emptyList()
        return Resource.Success(Unit)
    }
}

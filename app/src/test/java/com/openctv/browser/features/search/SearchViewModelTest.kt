package com.openctv.browser.features.search

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.common.Resource
import com.openctv.browser.features.bookmarks.domain.Bookmark
import com.openctv.browser.features.bookmarks.domain.BookmarksContract
import com.openctv.browser.features.search.domain.RecentSearchesContract
import com.openctv.browser.features.search.domain.SearchMode
import com.openctv.browser.features.search.domain.SearchRequest
import com.openctv.browser.features.search.domain.Suggestion
import com.openctv.browser.features.search.ui.SearchEvent
import com.openctv.browser.features.search.ui.SearchProblem
import com.openctv.browser.features.search.ui.SearchViewModel
import com.openctv.browser.features.settings.domain.SearchEngine
import com.openctv.browser.features.settings.domain.Settings
import com.openctv.browser.features.settings.domain.SettingsContract
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val arte = Bookmark("https://arte.tv", "Arte", Instant.EPOCH)
    private val bookmarks = mockk<BookmarksContract> {
        every { getBookmarks() } returns flowOf(Resource.Success(listOf(arte)))
        coEvery { add(any(), any()) } returns Resource.Success(Unit)
    }
    private val recents = mockk<RecentSearchesContract> {
        every { getRecents() } returns flowOf(Resource.Success(listOf("meteo paris")))
        coEvery { record(any()) } returns Resource.Success(Unit)
        coEvery { clear() } returns Resource.Success(Unit)
    }
    private val storedSettings = MutableStateFlow<Resource<Settings>>(Resource.Success(Settings()))
    private val settings = mockk<SettingsContract> {
        every { settings } returns storedSettings
        every { current } answers { callOriginal() }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(mode: SearchMode = SearchMode.Browse, initialText: String = "") =
        SearchViewModel(SearchRequest(mode, initialText), bookmarks, recents, settings)

    @Test
    fun `Given arte is a favorite When the user types arte Then arte is proposed without any network call`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onQueryChange("arte")

            val suggestions = expectMostRecentItem().suggestions
            assertThat(suggestions).containsExactly(Suggestion.Favorite("https://arte.tv", "Arte"))
        }
    }

    @Test
    fun `Given DuckDuckGo When the user validates a domain Then the browser opens it and no search is remembered`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onQueryChange("arte.tv")
            viewModel.onSubmit()

            assertThat(awaitItem()).isEqualTo(SearchEvent.Open("https://arte.tv"))
        }
        coVerify(exactly = 0) { recents.record(any()) }
    }

    @Test
    fun `Given Google When the user validates words Then the Google results open and the search is remembered`() = runTest {
        storedSettings.value = Resource.Success(Settings(searchEngine = SearchEngine.Google))
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onQueryChange("arte documentaire")
            viewModel.onSubmit()

            assertThat(awaitItem()).isEqualTo(
                SearchEvent.Open("https://www.google.com/search?q=arte+documentaire"),
            )
        }
        coVerifyOrder { recents.record("arte documentaire") }
    }

    @Test
    fun `Given a storage failure When a search is validated Then the search still opens`() = runTest {
        coEvery { recents.record(any()) } returns Resource.Error("disk full")
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onQueryChange("meteo")
            viewModel.onSubmit()

            assertThat(awaitItem()).isInstanceOf(SearchEvent.Open::class.java)
        }
    }

    @Test
    fun `Given a favorite suggestion When the user selects it Then the favorite opens directly`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onSuggestionSelected(Suggestion.Favorite("https://arte.tv", "Arte"))

            assertThat(awaitItem()).isEqualTo(SearchEvent.Open("https://arte.tv"))
        }
    }

    @Test
    fun `Given a recent search When the user selects it Then it is searched again even if it looks like an address`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onSuggestionSelected(Suggestion.Recent("arte.tv"))

            assertThat(awaitItem()).isEqualTo(
                SearchEvent.Open("https://duckduckgo.com/?q=arte.tv"),
            )
        }
    }

    @Test
    fun `Given an empty input When the user submits Then nothing happens`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onSubmit()

            expectNoEvents()
        }
    }

    @Test
    fun `Given an empty input and stored recents When the screen opens Then recents and the clear action are offered`() = runTest {
        viewModel().uiState.test {
            val state = expectMostRecentItem()

            assertThat(state.suggestions).containsExactly(Suggestion.Recent("meteo paris"))
            assertThat(state.canClearRecents).isTrue()
        }
    }

    @Test
    fun `Given recents When the user clears them Then the history is erased`() = runTest {
        viewModel().onClearRecents()

        coVerify { recents.clear() }
    }

    @Test
    fun `Given add bookmark mode When the user validates an address Then it is saved and the input closes`() = runTest {
        val viewModel = viewModel(SearchMode.AddBookmark)

        viewModel.events.test {
            viewModel.onQueryChange("www.wikipedia.org")
            viewModel.onSubmit()

            assertThat(awaitItem()).isEqualTo(SearchEvent.Close)
        }
        coVerify { bookmarks.add("https://www.wikipedia.org", "wikipedia.org") }
    }

    @Test
    fun `Given add bookmark mode When the user validates plain words Then nothing is saved and the problem is shown`() = runTest {
        val viewModel = viewModel(SearchMode.AddBookmark)

        viewModel.uiState.test {
            viewModel.onQueryChange("des mots")
            viewModel.onSubmit()

            assertThat(expectMostRecentItem().problem).isEqualTo(SearchProblem.NotAnAddress)

            viewModel.onQueryChange("arte.tv")
            assertThat(expectMostRecentItem().problem).isNull()
        }
        coVerify(exactly = 0) { bookmarks.add(any(), any()) }
    }

    @Test
    fun `Given add bookmark mode When saving fails Then the user stays and sees the error`() = runTest {
        coEvery { bookmarks.add(any(), any()) } returns Resource.Error("disk")
        val viewModel = viewModel(SearchMode.AddBookmark)

        viewModel.uiState.test {
            viewModel.onQueryChange("arte.tv")
            viewModel.onSubmit()

            assertThat(expectMostRecentItem().problem).isEqualTo(SearchProblem.SaveFailed)
        }
    }

    @Test
    fun `Given the address bar of a page When the search opens prefilled Then the field starts with the current url`() = runTest {
        val viewModel = viewModel(initialText = "https://arte.tv/fr")

        assertThat(viewModel.query.value).isEqualTo("https://arte.tv/fr")
    }
}

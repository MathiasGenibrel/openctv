package com.openctv.browser.features.home

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.navigation.Destination
import com.openctv.browser.features.bookmarks.domain.Bookmark
import com.openctv.browser.features.bookmarks.domain.BookmarksContract
import com.openctv.browser.features.home.domain.DoubleBackPress
import com.openctv.browser.features.home.domain.HomeFocus
import com.openctv.browser.features.home.ui.HomeEvent
import com.openctv.browser.features.home.ui.HomeViewModel
import com.openctv.browser.features.search.domain.SearchMode
import com.openctv.browser.features.search.domain.SearchRequest
import com.openctv.browser.features.settings.domain.SearchEngine
import com.openctv.browser.features.settings.domain.Settings
import com.openctv.browser.features.settings.domain.SettingsContract
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val bookmarks = mockk<BookmarksContract>()
    private val storedSettings = MutableStateFlow<Resource<Settings>>(Resource.Success(Settings()))
    private val settings = mockk<SettingsContract> {
        every { settings } returns storedSettings
        every { current } answers { callOriginal() }
    }
    private val clock = MutableClock()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = HomeViewModel(bookmarks, settings, DoubleBackPress(clock, Duration.ofSeconds(2)))

    private val arte = Bookmark("https://www.arte.tv/fr", "Arte", Instant.EPOCH)

    @Test
    fun `Given saved favorites When the home loads Then each favorite shows its name, bare domain and monogram`() = runTest {
        every { bookmarks.getBookmarks() } returns flowOf(Resource.Success(listOf(arte)))

        viewModel().favorites.test {
            val favorites = (expectMostRecentItem() as Resource.Success).data
            assertThat(favorites).hasSize(1)
            assertThat(favorites[0].name).isEqualTo("Arte")
            assertThat(favorites[0].domain).isEqualTo("arte.tv")
            assertThat(favorites[0].monogram).isEqualTo("A")
        }
    }

    private val wiki = Bookmark("https://wikipedia.org", "Wikipedia", Instant.EPOCH)
    private val twitch = Bookmark("https://twitch.tv", "Twitch", Instant.EPOCH)

    @Test
    fun `Given a favorite tile When the user holds OK Then its menu opens and Back closes it keeping the focus on the tile`() =
        runTest {
            every { bookmarks.getBookmarks() } returns flowOf(Resource.Success(listOf(arte, wiki)))
            val viewModel = viewModel()
            viewModel.favorites.test {
                val favorites = (expectMostRecentItem() as Resource.Success).data

                viewModel.onFavoriteMenuRequested(favorites[1])
                assertThat(viewModel.favoriteMenu.value).isEqualTo(favorites[1])

                viewModel.onFavoriteMenuClosed()
                assertThat(viewModel.favoriteMenu.value).isNull()
                assertThat(viewModel.focusToRestore()).isEqualTo(HomeFocus.Favorite("https://wikipedia.org"))
            }
        }

    @Test
    fun `Given a favorite menu When the user chooses Remove Then the bookmark is removed and focus moves to the next tile`() =
        runTest {
            every { bookmarks.getBookmarks() } returns flowOf(Resource.Success(listOf(arte, wiki, twitch)))
            coEvery { bookmarks.remove(any()) } returns Resource.Success(Unit)
            val viewModel = viewModel()
            viewModel.favorites.test {
                val favorites = (expectMostRecentItem() as Resource.Success).data
                viewModel.onFavoriteMenuRequested(favorites[1])

                viewModel.onRemoveFromMenu()

                coVerify { bookmarks.remove("https://wikipedia.org") }
                assertThat(viewModel.favoriteMenu.value).isNull()
                assertThat(viewModel.focusToRestore()).isEqualTo(HomeFocus.Favorite("https://twitch.tv"))
            }
        }

    @Test
    fun `Given the last favorite in the grid When it is removed Then focus moves to the previous tile`() = runTest {
        every { bookmarks.getBookmarks() } returns flowOf(Resource.Success(listOf(arte, wiki)))
        coEvery { bookmarks.remove(any()) } returns Resource.Success(Unit)
        val viewModel = viewModel()
        viewModel.favorites.test {
            val favorites = (expectMostRecentItem() as Resource.Success).data
            viewModel.onFavoriteMenuRequested(favorites[1])

            viewModel.onRemoveFromMenu()

            assertThat(viewModel.focusToRestore()).isEqualTo(HomeFocus.Favorite("https://www.arte.tv/fr"))
        }
    }

    @Test
    fun `Given a favorite menu When the user chooses Open Then the site opens and the menu closes`() = runTest {
        every { bookmarks.getBookmarks() } returns flowOf(Resource.Success(listOf(arte)))
        val viewModel = viewModel()
        viewModel.favorites.test {
            val favorite = (expectMostRecentItem() as Resource.Success).data[0]
            viewModel.onFavoriteMenuRequested(favorite)
            viewModel.events.test {
                viewModel.onOpenFromMenu()

                assertThat(awaitItem()).isEqualTo(HomeEvent.Navigate(Destination.Browser("https://www.arte.tv/fr")))
            }
            assertThat(viewModel.favoriteMenu.value).isNull()
        }
    }

    @Test
    fun `Given no favorite When the home loads Then the list is empty so the empty state is shown`() = runTest {
        every { bookmarks.getBookmarks() } returns flowOf(Resource.Success(emptyList()))

        viewModel().favorites.test {
            assertThat(expectMostRecentItem()).isEqualTo(Resource.Success(emptyList<Any>()))
        }
    }

    @Test
    fun `Given no favorite When the home loads Then the hold OK hint for favorite options is hidden`() = runTest {
        every { bookmarks.getBookmarks() } returns flowOf(Resource.Success(emptyList()))

        viewModel().isFavoriteOptionsHintVisible.test {
            assertThat(expectMostRecentItem()).isFalse()
        }
    }

    @Test
    fun `Given a saved favorite When the home loads Then the hold OK hint for favorite options is shown`() = runTest {
        every { bookmarks.getBookmarks() } returns flowOf(Resource.Success(listOf(arte)))

        viewModel().isFavoriteOptionsHintVisible.test {
            assertThat(expectMostRecentItem()).isTrue()
        }
    }

    @Test
    fun `Given a storage failure When the home loads Then the error is exposed`() = runTest {
        every { bookmarks.getBookmarks() } returns flowOf(Resource.Error("disk"))

        viewModel().favorites.test {
            assertThat(expectMostRecentItem()).isEqualTo(Resource.Error("disk"))
        }
    }

    @Test
    fun `Given the favorite Arte When the user opens it Then the browser destination targets its url`() = runTest {
        every { bookmarks.getBookmarks() } returns flowOf(Resource.Success(listOf(arte)))
        val viewModel = viewModel()

        viewModel.favorites.test {
            val favorite = (expectMostRecentItem() as Resource.Success).data.first()
            viewModel.events.test {
                viewModel.onOpenFavorite(favorite)
                assertThat(awaitItem()).isEqualTo(HomeEvent.Navigate(Destination.Browser("https://www.arte.tv/fr")))
            }
        }
    }

    @Test
    fun `Given Qwant chosen in the settings When the home opens Then the search bar names Qwant`() = runTest {
        every { bookmarks.getBookmarks() } returns flowOf(Resource.Success(emptyList()))
        storedSettings.value = Resource.Success(Settings(searchEngine = SearchEngine.Qwant))

        viewModel().searchEngineName.test {
            assertThat(expectMostRecentItem()).isEqualTo("Qwant")
        }
    }

    @Test
    fun `Given unreadable settings When the home opens Then the search bar names the default engine`() = runTest {
        every { bookmarks.getBookmarks() } returns flowOf(Resource.Success(emptyList()))
        storedSettings.value = Resource.Error("disk")

        viewModel().searchEngineName.test {
            assertThat(expectMostRecentItem()).isEqualTo("DuckDuckGo")
        }
    }

    @Test
    fun `Given the home When Back is pressed once Then the app stays open and warns`() = runTest {
        every { bookmarks.getBookmarks() } returns flowOf(Resource.Success(emptyList()))
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onBack()
            assertThat(viewModel.isExitHintVisible.value).isTrue()
            expectNoEvents()
        }
    }

    @Test
    fun `Given the exit warning When Back is pressed again quickly Then the app exits`() = runTest {
        every { bookmarks.getBookmarks() } returns flowOf(Resource.Success(emptyList()))
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onBack()
            clock.advance(Duration.ofSeconds(1))
            advanceTimeBy(1_000)
            viewModel.onBack()
            assertThat(awaitItem()).isEqualTo(HomeEvent.Exit)
        }
    }

    @Test
    fun `Given the exit warning When Back is pressed again too late Then it warns again instead of exiting`() = runTest {
        every { bookmarks.getBookmarks() } returns flowOf(Resource.Success(emptyList()))
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onBack()
            clock.advance(Duration.ofSeconds(5))
            advanceTimeBy(5_000)
            viewModel.onBack()
            assertThat(viewModel.isExitHintVisible.value).isTrue()
            expectNoEvents()
        }
    }

    @Test
    fun `Given the exit warning When the exit window elapses Then the warning disappears`() = runTest {
        every { bookmarks.getBookmarks() } returns flowOf(Resource.Success(emptyList()))
        val viewModel = viewModel()

        viewModel.onBack()
        advanceTimeBy(1_999)
        assertThat(viewModel.isExitHintVisible.value).isTrue()
        advanceTimeBy(2)
        assertThat(viewModel.isExitHintVisible.value).isFalse()
    }

    @Test
    fun `Given the user opened Arte from the grid When he comes back home Then the Arte tile gets the focus back`() = runTest {
        every { bookmarks.getBookmarks() } returns flowOf(Resource.Success(listOf(arte)))
        val viewModel = viewModel()

        viewModel.favorites.test {
            val favorite = (expectMostRecentItem() as Resource.Success).data.first()
            viewModel.onFocused(HomeFocus.Favorite(favorite.url))
            viewModel.onOpenFavorite(favorite)
        }

        assertThat(viewModel.focusToRestore()).isEqualTo(HomeFocus.Favorite("https://www.arte.tv/fr"))
    }

    @Test
    fun `Given the options of Arte When Back closes them and the engine falls back on the search bar Then the Arte tile still gets the focus`() = runTest {
        every { bookmarks.getBookmarks() } returns flowOf(Resource.Success(listOf(arte)))
        val viewModel = viewModel()

        viewModel.favorites.test {
            val favorite = (expectMostRecentItem() as Resource.Success).data.first()
            viewModel.onFocused(HomeFocus.Favorite(favorite.url))
            viewModel.onFavoriteMenuRequested(favorite)

            viewModel.onFavoriteMenuClosed()
            viewModel.onFocused(HomeFocus.Search) // focus de repli quand le menu quitte la composition

            assertThat(viewModel.focusToRestore()).isEqualTo(HomeFocus.Favorite(favorite.url))
        }
    }

    @Test
    fun `Given the focus restored after the options closed When the user moves to the search bar Then the move is remembered again`() = runTest {
        every { bookmarks.getBookmarks() } returns flowOf(Resource.Success(listOf(arte)))
        val viewModel = viewModel()

        viewModel.favorites.test {
            val favorite = (expectMostRecentItem() as Resource.Success).data.first()
            viewModel.onFavoriteMenuRequested(favorite)
            viewModel.onFavoriteMenuClosed()
            viewModel.onFocusRestored()

            viewModel.onFocused(HomeFocus.Search)

            assertThat(viewModel.focusToRestore()).isEqualTo(HomeFocus.Search)
        }
    }

    @Test
    fun `Given two favorites When the user removes the first from its options Then the focus goes to the neighbour even if the engine falls back on the search bar`() = runTest {
        val wiki = Bookmark("https://www.wikipedia.org", "Wikipedia", Instant.EPOCH)
        every { bookmarks.getBookmarks() } returns flowOf(Resource.Success(listOf(arte, wiki)))
        coEvery { bookmarks.remove(any()) } returns Resource.Success(Unit)
        val viewModel = viewModel()

        viewModel.favorites.test {
            val first = (expectMostRecentItem() as Resource.Success).data.first()
            viewModel.onFavoriteMenuRequested(first)

            viewModel.onRemoveFromMenu()
            viewModel.onFocused(HomeFocus.Search)

            assertThat(viewModel.focusToRestore()).isEqualTo(HomeFocus.Favorite("https://www.wikipedia.org"))
        }
    }

    @Test
    fun `Given the add tile When the user adds a favorite and the input closes Then the focus returns to the add tile`() = runTest {
        every { bookmarks.getBookmarks() } returns flowOf(Resource.Success(listOf(arte)))
        val viewModel = viewModel()

        viewModel.favorites.test {
            expectMostRecentItem()
            viewModel.onFocused(HomeFocus.Add)
            viewModel.onAddBookmark()
            assertThat(viewModel.searchInput.value).isEqualTo(SearchRequest(SearchMode.AddBookmark))

            viewModel.onSearchClosed()

            assertThat(viewModel.searchInput.value).isNull()
            assertThat(viewModel.focusToRestore()).isEqualTo(HomeFocus.Add)
        }
    }

    @Test
    fun `Given the search open from the home When the user validates an address Then it closes and the browser opens it`() = runTest {
        every { bookmarks.getBookmarks() } returns flowOf(Resource.Success(emptyList()))
        val viewModel = viewModel()
        viewModel.onOpenSearch()
        assertThat(viewModel.searchInput.value).isEqualTo(SearchRequest(SearchMode.Browse))

        viewModel.events.test {
            viewModel.onAddressChosen("https://arte.tv")

            assertThat(awaitItem()).isEqualTo(HomeEvent.Navigate(Destination.Browser("https://arte.tv")))
        }
        assertThat(viewModel.searchInput.value).isNull()
    }

    @Test
    fun `Given the settings button When the user opens the settings Then the settings destination is requested`() = runTest {
        every { bookmarks.getBookmarks() } returns flowOf(Resource.Success(emptyList()))
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onOpenSettings()

            assertThat(awaitItem()).isEqualTo(HomeEvent.Navigate(Destination.Settings))
        }
    }

    @Test
    fun `Given the history button When the user opens the history Then the history destination is requested`() = runTest {
        every { bookmarks.getBookmarks() } returns flowOf(Resource.Success(emptyList()))
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onOpenHistory()

            assertThat(awaitItem()).isEqualTo(HomeEvent.Navigate(Destination.History))
        }
    }

    @Test
    fun `Given the exit warning When the user leaves the home and comes back Then the next Back only warns`() = runTest {
        every { bookmarks.getBookmarks() } returns flowOf(Resource.Success(emptyList()))
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onBack()
            viewModel.onOpenSettings()
            assertThat(awaitItem()).isEqualTo(HomeEvent.Navigate(Destination.Settings))
            assertThat(viewModel.isExitHintVisible.value).isFalse()

            clock.advance(Duration.ofSeconds(1))
            viewModel.onBack()

            assertThat(viewModel.isExitHintVisible.value).isTrue()
            expectNoEvents()
        }
    }

    private class MutableClock : Clock() {
        private var now: Instant = Instant.EPOCH
        fun advance(duration: Duration) { now += duration }
        override fun instant(): Instant = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?): Clock = this
    }
}

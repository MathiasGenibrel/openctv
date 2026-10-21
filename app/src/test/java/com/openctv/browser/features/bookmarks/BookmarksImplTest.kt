package com.openctv.browser.features.bookmarks

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.common.TestDispatcherProvider
import com.openctv.browser.features.bookmarks.data.BookmarkDao
import com.openctv.browser.features.bookmarks.data.BookmarkEntity
import com.openctv.browser.features.bookmarks.data.BookmarksImpl
import com.openctv.browser.features.bookmarks.domain.Bookmark
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BookmarksImplTest {

    private val now = Instant.parse("2026-10-20T10:00:00Z")
    private val dao = mockk<BookmarkDao>(relaxed = true)

    private fun TestScope.repository() = BookmarksImpl(
        dao = dao,
        dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `Given stored entities When listing Then they are mapped to domain after Loading`() = runTest {
        val entity = BookmarkEntity("https://arte.tv", "Arte", now)
        every { dao.observeAll() } returns flowOf(listOf(entity))

        repository().getBookmarks().test {
            assertThat(awaitItem()).isEqualTo(Resource.Loading)
            assertThat(awaitItem()).isEqualTo(
                Resource.Success(listOf(Bookmark("https://arte.tv", "Arte", now))),
            )
            awaitComplete()
        }
    }

    @Test
    fun `Given the home screen observes bookmarks When a site is added then removed Then the list is re-emitted each time without a new Loading`() =
        runTest {
            val arte = BookmarkEntity("https://arte.tv", "Arte", now)
            every { dao.observeAll() } returns flowOf(emptyList(), listOf(arte), emptyList())

            repository().getBookmarks().test {
                assertThat(awaitItem()).isEqualTo(Resource.Loading)
                assertThat(awaitItem()).isEqualTo(Resource.Success(emptyList<Bookmark>()))
                assertThat(awaitItem()).isEqualTo(
                    Resource.Success(listOf(Bookmark("https://arte.tv", "Arte", now))),
                )
                assertThat(awaitItem()).isEqualTo(Resource.Success(emptyList<Bookmark>()))
                awaitComplete()
            }
        }

    @Test
    fun `Given arte tv already bookmarked When the user adds it again with another spelling Then the same key is inserted so no duplicate exists`() =
        runTest {
            coEvery { dao.insert(any()) } returns Unit

            val result = repository().add("HTTPS://Arte.tv/", "Arte")

            assertThat(result).isEqualTo(Resource.Success(Unit))
            coVerify { dao.insert(BookmarkEntity("https://arte.tv", "Arte", now)) }
        }

    @Test
    fun `Given a blank title When adding Then the url is used as title`() = runTest {
        repository().add("https://arte.tv", "  ")

        coVerify { dao.insert(BookmarkEntity("https://arte.tv", "https://arte.tv", now)) }
    }

    @Test
    fun `Given arte tv is bookmarked When the user removes it Then the normalized url is deleted`() =
        runTest {
            val result = repository().remove("https://Arte.tv/")

            assertThat(result).isEqualTo(Resource.Success(Unit))
            coVerify { dao.delete("https://arte.tv") }
        }

    @Test
    fun `Given the storage fails When adding Then an Error is returned`() = runTest {
        coEvery { dao.insert(any()) } throws IllegalStateException("disk full")

        val result = repository().add("https://arte.tv", "Arte")

        assertThat(result).isEqualTo(Resource.Error("disk full"))
    }

    @Test
    fun `Given the storage fails When removing Then an Error is returned`() = runTest {
        coEvery { dao.delete(any()) } throws IllegalStateException("locked")

        assertThat(repository().remove("https://arte.tv")).isEqualTo(Resource.Error("locked"))
    }

    @Test
    fun `Given the storage fails When listing Then the flow emits an Error`() = runTest {
        every { dao.observeAll() } returns flow { throw IllegalStateException("corrupted") }

        repository().getBookmarks().test {
            assertThat(awaitItem()).isEqualTo(Resource.Loading)
            assertThat(awaitItem()).isEqualTo(Resource.Error("corrupted"))
            awaitComplete()
        }
    }

    @Test
    fun `Given wikipedia org is bookmarked When the site redirects to https www wikipedia org Then it is still bookmarked`() =
        runTest {
            every { dao.observeAll() } returns flowOf(listOf(BookmarkEntity("https://wikipedia.org", "Wikipedia", now)))

            repository().isBookmarked("https://www.wikipedia.org/").test {
                assertThat(awaitItem()).isEqualTo(Resource.Loading)
                assertThat(awaitItem()).isEqualTo(Resource.Success(true))
                awaitComplete()
            }
        }

    @Test
    fun `Given arte tv is bookmarked When asking for another page of another site Then it is not bookmarked`() =
        runTest {
            every { dao.observeAll() } returns flowOf(listOf(BookmarkEntity("https://arte.tv", "Arte", now)))

            repository().isBookmarked("https://www.arte.fr").test {
                assertThat(awaitItem()).isEqualTo(Resource.Loading)
                assertThat(awaitItem()).isEqualTo(Resource.Success(false))
                awaitComplete()
            }
        }

    @Test
    fun `Given wikipedia org is bookmarked When the user adds www wikipedia org Then nothing is inserted`() = runTest {
        coEvery { dao.getAll() } returns listOf(BookmarkEntity("https://wikipedia.org", "Wikipedia", now))

        val result = repository().add("https://www.wikipedia.org/", "wikipedia.org")

        assertThat(result).isEqualTo(Resource.Success(Unit))
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun `Given wikipedia org is bookmarked When the user removes it from www wikipedia org Then the stored entry is deleted`() =
        runTest {
            coEvery { dao.getAll() } returns listOf(BookmarkEntity("https://wikipedia.org", "Wikipedia", now))

            val result = repository().remove("https://www.wikipedia.org/")

            assertThat(result).isEqualTo(Resource.Success(Unit))
            coVerify { dao.delete("https://wikipedia.org") }
        }

    @Test
    fun `Given two stored spellings of one site When listing Then only the oldest is shown and no favorite is lost`() =
        runTest {
            val bare = BookmarkEntity("https://wikipedia.org", "Wikipedia", now)
            val www = BookmarkEntity("https://www.wikipedia.org", "wikipedia.org", now.plusSeconds(5))
            val arte = BookmarkEntity("https://arte.tv", "Arte", now.plusSeconds(9))
            every { dao.observeAll() } returns flowOf(listOf(bare, www, arte))

            repository().getBookmarks().test {
                assertThat(awaitItem()).isEqualTo(Resource.Loading)
                val shown = (awaitItem() as Resource.Success).data
                assertThat(shown.map { it.url }).containsExactly("https://wikipedia.org", "https://arte.tv").inOrder()
                awaitComplete()
            }
        }

    @Test
    fun `Given both spellings stored When the user removes the site Then both are deleted`() = runTest {
        coEvery { dao.getAll() } returns listOf(
            BookmarkEntity("https://wikipedia.org", "Wikipedia", now),
            BookmarkEntity("https://www.wikipedia.org", "wikipedia.org", now),
        )

        repository().remove("https://www.wikipedia.org")

        coVerify { dao.delete("https://wikipedia.org") }
        coVerify { dao.delete("https://www.wikipedia.org") }
    }
}

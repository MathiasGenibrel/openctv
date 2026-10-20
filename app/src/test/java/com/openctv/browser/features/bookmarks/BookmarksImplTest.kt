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
    fun `Given a url spelled differently When asking if bookmarked Then the normalized url is queried`() =
        runTest {
            every { dao.observeExists("https://arte.tv") } returns flowOf(true)

            repository().isBookmarked("https://ARTE.tv/").test {
                assertThat(awaitItem()).isEqualTo(Resource.Loading)
                assertThat(awaitItem()).isEqualTo(Resource.Success(true))
                awaitComplete()
            }
        }
}

package com.openctv.browser.features.bookmarks.data

import com.openctv.browser.core.common.DispatcherProvider
import com.openctv.browser.core.common.Resource
import com.openctv.browser.features.bookmarks.domain.Bookmark
import com.openctv.browser.features.bookmarks.domain.BookmarkUrl
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class BookmarksImpl(
    private val dao: BookmarkDao,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock = Clock.systemUTC(),
) : BookmarksContract {

    override fun getBookmarks(): Flow<Resource<List<Bookmark>>> =
        dao.observeAll().asResource { entities -> entities.map { it.toDomain() } }

    override fun isBookmarked(url: String): Flow<Resource<Boolean>> =
        dao.observeExists(BookmarkUrl.normalize(url)).asResource { it }

    override suspend fun add(url: String, title: String): Resource<Unit> = write {
        val normalized = BookmarkUrl.normalize(url)
        dao.insert(BookmarkEntity(normalized, title.ifBlank { normalized }, Instant.now(clock)))
    }

    override suspend fun remove(url: String): Resource<Unit> = write {
        dao.delete(BookmarkUrl.normalize(url))
    }

    private suspend fun write(block: suspend () -> Unit): Resource<Unit> =
        withContext(dispatchers.io) {
            try {
                block()
                Resource.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Resource.Error(e.message)
            }
        }

    private fun <S, T> Flow<S>.asResource(transform: (S) -> T): Flow<Resource<T>> =
        map<S, Resource<T>> { Resource.Success(transform(it)) }
            .catch { emit(Resource.Error(it.message)) }
            .flowOn(dispatchers.io)
            .let { upstream ->
                flow {
                    emit(Resource.Loading)
                    emitAll(upstream)
                }
            }
}

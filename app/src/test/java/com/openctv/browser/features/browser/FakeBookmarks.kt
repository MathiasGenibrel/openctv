package com.openctv.browser.features.browser

import com.openctv.browser.core.common.Resource
import com.openctv.browser.features.bookmarks.domain.Bookmark
import com.openctv.browser.features.bookmarks.domain.BookmarkUrl
import com.openctv.browser.features.bookmarks.domain.BookmarksContract
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Favoris en mémoire, réactifs comme le vrai repository. */
class FakeBookmarks : BookmarksContract {
    private val stored = MutableStateFlow<Map<String, Pair<String, String>>>(emptyMap())

    val urls: Set<String> get() = stored.value.values.map { it.first }.toSet()

    /** Non nul : ajouts et retraits échouent avec ce message, comme un stockage indisponible. */
    var failure: String? = null

    fun seed(url: String) {
        stored.value += BookmarkUrl.key(url) to (BookmarkUrl.normalize(url) to url)
    }

    override fun getBookmarks(): Flow<Resource<List<Bookmark>>> =
        stored.map { map -> Resource.Success(map.values.map { (url, title) -> Bookmark(url, title, Instant.EPOCH) }) }

    override fun isBookmarked(url: String): Flow<Resource<Boolean>> =
        stored.map { Resource.Success(BookmarkUrl.key(url) in it) }

    override suspend fun add(url: String, title: String): Resource<Unit> {
        failure?.let { return Resource.Error(it) }
        stored.value += BookmarkUrl.key(url) to (BookmarkUrl.normalize(url) to title)
        return Resource.Success(Unit)
    }

    override suspend fun remove(url: String): Resource<Unit> {
        failure?.let { return Resource.Error(it) }
        stored.value -= BookmarkUrl.key(url)
        return Resource.Success(Unit)
    }
}

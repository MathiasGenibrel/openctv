package com.openctv.browser.features.bookmarks.data

import com.openctv.browser.core.common.Resource
import com.openctv.browser.features.bookmarks.domain.Bookmark
import kotlinx.coroutines.flow.Flow

interface BookmarksContract {
    /** Favoris par ordre d'ajout. */
    fun getBookmarks(): Flow<Resource<List<Bookmark>>>

    fun isBookmarked(url: String): Flow<Resource<Boolean>>

    /** Sans effet si l'URL (normalisée) est déjà favorite. */
    suspend fun add(url: String, title: String): Resource<Unit>

    suspend fun remove(url: String): Resource<Unit>
}

package com.openctv.browser.features.bookmarks.data

import com.openctv.browser.features.bookmarks.domain.Bookmark

fun BookmarkEntity.toDomain() = Bookmark(url = url, title = title, addedAt = addedAt)

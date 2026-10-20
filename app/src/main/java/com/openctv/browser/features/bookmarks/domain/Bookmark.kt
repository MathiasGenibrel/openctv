package com.openctv.browser.features.bookmarks.domain

import java.time.Instant

data class Bookmark(
    val url: String,
    val title: String,
    val addedAt: Instant,
)

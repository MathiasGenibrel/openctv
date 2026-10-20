package com.openctv.browser.features.bookmarks.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/** `url` est l'URL normalisée : elle sert de clé primaire, donc pas de doublon possible. */
@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val url: String,
    val title: String,
    val addedAt: Instant,
)

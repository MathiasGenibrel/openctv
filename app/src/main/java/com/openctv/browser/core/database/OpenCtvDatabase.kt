package com.openctv.browser.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.openctv.browser.features.bookmarks.data.BookmarkDao
import com.openctv.browser.features.bookmarks.data.BookmarkEntity
import com.openctv.browser.features.history.data.HistoryDao
import com.openctv.browser.features.history.data.HistoryEntryEntity

/** Base Room de l'app. Les données restent sur l'appareil (pas de sauvegarde cloud). */
@Database(entities = [BookmarkEntity::class, HistoryEntryEntity::class], version = 2, exportSchema = true)
@TypeConverters(InstantConverters::class)
abstract class OpenCtvDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
}

package com.openctv.browser.features.history.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/** Une page visitée. L'index sur [visitedAt] sert le tri, l'élagage par âge et la borne de taille. */
@Entity(tableName = "history_entries", indices = [Index(value = ["visitedAt"])])
data class HistoryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val visitedAt: Instant,
)

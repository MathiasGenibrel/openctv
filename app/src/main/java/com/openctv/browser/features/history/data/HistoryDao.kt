package com.openctv.browser.features.history.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_entries ORDER BY visitedAt DESC, id DESC")
    fun observeAll(): Flow<List<HistoryEntryEntity>>

    @Query("SELECT * FROM history_entries ORDER BY visitedAt DESC, id DESC LIMIT 1")
    suspend fun latest(): HistoryEntryEntity?

    @Insert
    suspend fun insert(entity: HistoryEntryEntity)

    @Update
    suspend fun update(entity: HistoryEntryEntity)

    @Query("DELETE FROM history_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history_entries")
    suspend fun deleteAll()

    @Query("DELETE FROM history_entries WHERE visitedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Instant)

    /** Ne garde que les [max] visites les plus récentes. */
    @Query(
        "DELETE FROM history_entries WHERE id NOT IN " +
            "(SELECT id FROM history_entries ORDER BY visitedAt DESC, id DESC LIMIT :max)",
    )
    suspend fun keepLatest(max: Int)
}

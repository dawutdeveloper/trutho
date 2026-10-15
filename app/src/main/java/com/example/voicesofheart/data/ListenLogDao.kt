package com.example.voicesofheart.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ListenLogDao {
    @Insert
    suspend fun insert(log: ListenLog)

    @Insert
    suspend fun insertAll(logs: List<ListenLog>)

    @Query("SELECT * FROM listen_log")
    suspend fun getAll(): List<ListenLog>

    @Query("SELECT COUNT(*) FROM listen_log WHERE listenedAt >= :since")
    fun observeCountSince(since: Long): Flow<Int>

    @Query("SELECT listenedAt FROM listen_log ORDER BY listenedAt DESC")
    fun observeAllTimestamps(): Flow<List<Long>>

    @Query(
        "SELECT songId, COUNT(*) AS count FROM listen_log " +
                "GROUP BY songId ORDER BY count DESC, MAX(listenedAt) DESC LIMIT 10"
    )
    fun observeTopSongs(): Flow<List<SongCount>>
}
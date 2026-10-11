package com.example.voicesofheart.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ListenLogDao {
    @Insert
    suspend fun insert(log: ListenLog)

    @Query("SELECT COUNT(*) FROM listen_log WHERE listenedAt >= :since")
    fun observeCountSince(since: Long): Flow<Int>
}
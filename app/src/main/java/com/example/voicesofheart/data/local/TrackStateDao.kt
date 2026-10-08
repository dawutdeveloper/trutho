package com.example.voicesofheart.data.local


import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackStateDao {
    @Query("SELECT * FROM track_states WHERE songId = :songId")
    fun observe(songId: Long): Flow<TrackState?>

    @Query("SELECT * FROM track_states")
    fun observeAll(): Flow<List<TrackState>>

    @Query("SELECT * FROM track_states WHERE songId = :songId")
    suspend fun get(songId: Long): TrackState?

    @Upsert
    suspend fun upsert(state: TrackState)
}
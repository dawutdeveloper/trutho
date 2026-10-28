package com.dawutdeveloper.trutho.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorite_songs")
data class FavoriteSong(@PrimaryKey val songId: Long)

@Dao
interface FavoriteSongDao {
    @Query("SELECT songId FROM favorite_songs")
    fun observeIds(): Flow<List<Long>>
    @Query("SELECT * FROM favorite_songs")
    suspend fun getAll(): List<FavoriteSong>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(song: FavoriteSong)
    @Query("DELETE FROM favorite_songs WHERE songId = :id")
    suspend fun remove(id: Long)
    @Query("DELETE FROM favorite_songs WHERE songId IN (:ids)")
    suspend fun removeSongs(ids: List<Long>)
    @Transaction
    suspend fun toggle(id: Long) {
        if (getAll().any { it.songId == id }) remove(id) else add(FavoriteSong(id))
    }
}

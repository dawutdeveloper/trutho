package com.example.voicesofheart.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FolderDao {

    @Query("SELECT * FROM folders ORDER BY createdAt DESC")
    abstract fun observeFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE id = :id")
    abstract fun observeFolder(id: Long): Flow<Folder?>

    @Query("SELECT songId FROM folder_songs WHERE folderId = :folderId ORDER BY addedAt ASC")
    abstract fun observeSongIds(folderId: Long): Flow<List<Long>>

    @Query("SELECT folderId, COUNT(*) AS count FROM folder_songs GROUP BY folderId")
    abstract fun observeCounts(): Flow<List<FolderCount>>

    @Insert
    abstract suspend fun insertFolder(folder: Folder): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun addSong(link: FolderSong)

    @Query("DELETE FROM folder_songs WHERE folderId = :folderId AND songId = :songId")
    abstract suspend fun removeSong(folderId: Long, songId: Long)

    @Query("DELETE FROM folder_songs WHERE folderId = :folderId")
    abstract suspend fun deleteLinks(folderId: Long)

    @Query("DELETE FROM folders WHERE id = :folderId")
    abstract suspend fun deleteFolderRow(folderId: Long)

    @Transaction
    open suspend fun deleteFolder(folderId: Long) {
        deleteLinks(folderId)
        deleteFolderRow(folderId)
    }
}
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

    @Query("SELECT * FROM folders")
    abstract suspend fun getAllFolders(): List<Folder>

    @Query("SELECT * FROM folder_songs")
    abstract suspend fun getAllLinks(): List<FolderSong>

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

    @Query("DELETE FROM folder_songs WHERE songId IN (:songIds)")
    abstract suspend fun removeSongsEverywhere(songIds: List<Long>)

    @Transaction
    open suspend fun deleteFolder(folderId: Long) {
        deleteLinks(folderId)
        deleteFolderRow(folderId)
    }

    @Transaction
    open suspend fun addSongs(folderId: Long, songIds: List<Long>) {
        val now = System.currentTimeMillis()
        songIds.forEachIndexed { index, songId ->
            addSong(FolderSong(folderId = folderId, songId = songId, addedAt = now + index))
        }
    }

    @Transaction
    open suspend fun removeSongs(folderId: Long, songIds: List<Long>) {
        songIds.forEach { songId -> removeSong(folderId, songId) }
    }

    @Transaction
    open suspend fun moveSongs(fromFolderId: Long, toFolderId: Long, songIds: List<Long>) {
        addSongs(toFolderId, songIds)
        removeSongs(fromFolderId, songIds)
    }
}
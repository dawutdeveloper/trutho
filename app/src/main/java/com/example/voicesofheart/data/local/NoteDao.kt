package com.example.voicesofheart.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query(
        "SELECT * FROM notes WHERE songId = :songId " +
                "ORDER BY CASE WHEN timestampMs IS NULL THEN 1 ELSE 0 END, " +
                "timestampMs ASC, createdAt DESC"
    )
    fun getNotesForSong(songId: Long): Flow<List<Note>>

    @Insert
    suspend fun insertNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("DELETE FROM notes WHERE songId IN (:songIds)")
    suspend fun deleteForSongs(songIds: List<Long>)
}
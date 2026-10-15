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

    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeNote(id: Int): Flow<Note?>

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<Note>

    @Insert
    suspend fun insertNote(note: Note)

    @Insert
    suspend fun insertAll(notes: List<Note>)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("UPDATE notes SET text = :text WHERE id = :id")
    suspend fun updateText(id: Int, text: String)

    @Query("DELETE FROM notes WHERE songId IN (:songIds)")
    suspend fun deleteForSongs(songIds: List<Long>)
}
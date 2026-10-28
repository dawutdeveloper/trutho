package com.dawutdeveloper.trutho.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val songId: Long,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val timestampMs: Long? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val isFavorite: Boolean = false
)
package com.example.voicesofheart.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "listen_log")
data class ListenLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val listenedAt: Long = System.currentTimeMillis()
)
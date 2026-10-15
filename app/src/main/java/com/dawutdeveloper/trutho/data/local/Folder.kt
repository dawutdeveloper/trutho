package com.dawutdeveloper.trutho.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "folder_songs", primaryKeys = ["folderId", "songId"])
data class FolderSong(
    val folderId: Long,
    val songId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

data class FolderCount(
    val folderId: Long,
    val count: Int
)
package com.example.voicesofheart.domain

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val uri: Uri,
    val dateAdded: Long = 0L
)
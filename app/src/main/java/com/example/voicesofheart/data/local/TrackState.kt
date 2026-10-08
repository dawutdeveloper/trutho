package com.example.voicesofheart.data.local


import androidx.room.Entity
import androidx.room.PrimaryKey

enum class KaizenStatus(val label: String) {
    MUST_LISTEN("Must listen"),
    CURRENTLY_LISTENING("Listening"),
    LISTENED("Listened"),
    REPEAT("Repeat")
}

@Entity(tableName = "track_states")
data class TrackState(
    @PrimaryKey val songId: Long,
    val status: KaizenStatus,
    val targetRepeats: Int = 2,
    val completedRepeats: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
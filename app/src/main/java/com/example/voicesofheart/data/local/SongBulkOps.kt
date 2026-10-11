package com.example.voicesofheart.data.local

// Sets the same Kaizen status on many songs at once
suspend fun AppDatabase.setKaizenStatus(songIds: List<Long>, status: KaizenStatus) {
    val dao = trackStateDao()
    val now = System.currentTimeMillis()
    songIds.forEachIndexed { index, id ->
        val existing = dao.get(id)
        val updated = if (existing == null) {
            TrackState(songId = id, status = status, updatedAt = now + index)
        } else {
            existing.copy(
                status = status,
                completedRepeats = if (status == KaizenStatus.REPEAT &&
                    existing.status != KaizenStatus.REPEAT
                ) {
                    0
                } else {
                    existing.completedRepeats
                },
                updatedAt = now + index
            )
        }
        dao.upsert(updated)
    }
}

// Removes notes, Kaizen status and folder entries of songs that no longer exist.
// The listening log is kept on purpose, so your streaks stay intact.
suspend fun AppDatabase.forgetSongs(songIds: Collection<Long>) {
    songIds.toList().chunked(500).forEach { chunk ->
        noteDao().deleteForSongs(chunk)
        trackStateDao().deleteForSongs(chunk)
        folderDao().removeSongsEverywhere(chunk)
    }
}
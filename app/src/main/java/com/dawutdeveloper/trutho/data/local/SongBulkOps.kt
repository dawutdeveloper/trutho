package com.dawutdeveloper.trutho.data.local

// A song has one Kaizen status at a time. Updating an existing song moves it
// between stages rather than creating a duplicate.
suspend fun AppDatabase.setKaizenStatus(songIds: List<Long>, status: KaizenStatus) {
    val dao = trackStateDao()
    val now = System.currentTimeMillis()

    songIds.distinct().forEachIndexed { index, id ->
        val existing = dao.get(id)
        val updated = if (existing == null) {
            TrackState(songId = id, status = status, updatedAt = now + index)
        } else {
            existing.copy(
                status = status,
                completedRepeats = if (
                    status == KaizenStatus.REPEAT &&
                    existing.status != KaizenStatus.REPEAT
                ) 0 else existing.completedRepeats,
                updatedAt = now + index
            )
        }
        dao.upsert(updated)
    }
}

// Remove related records for songs deleted from the device.
// Listening history remains intentionally intact.
suspend fun AppDatabase.forgetSongs(songIds: Collection<Long>) {
    songIds.distinct().chunked(500).forEach { chunk ->
        favoriteSongDao().removeSongs(chunk)
        noteDao().deleteForSongs(chunk)
        trackStateDao().deleteForSongs(chunk)
        folderDao().removeSongsEverywhere(chunk)
    }
}

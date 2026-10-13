package com.example.voicesofheart.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import com.example.voicesofheart.domain.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ArtworkLoader {

    private val cache = object : LruCache<Long, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount
    }

    // Songs we already know have no artwork, so we don't retry them
    private val withoutArt = mutableSetOf<Long>()

    fun cached(songId: Long): Bitmap? = cache.get(songId)

    private fun knownMissing(songId: Long): Boolean {
        return synchronized(withoutArt) { withoutArt.contains(songId) }
    }

    suspend fun load(context: Context, song: Song): Bitmap? = withContext(Dispatchers.IO) {
        val hit = cache.get(song.id)
        if (hit != null) return@withContext hit
        if (knownMissing(song.id)) return@withContext null

        val app = context.applicationContext

        var bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                app.contentResolver.loadThumbnail(song.uri, Size(1000, 1000), null)
            }.getOrNull()
        } else {
            null
        }

        if (bitmap == null) {
            bitmap = runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(app, song.uri)
                    val bytes = retriever.embeddedPicture
                    if (bytes != null) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } else {
                        null
                    }
                } finally {
                    retriever.release()
                }
            }.getOrNull()
        }

        if (bitmap != null) {
            cache.put(song.id, bitmap)
        } else {
            synchronized(withoutArt) { withoutArt.add(song.id) }
        }
        bitmap
    }
}
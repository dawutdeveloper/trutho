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

    private const val BIG_SIZE = 1000
    private const val THUMB_SIZE = 256

    private val bigCache: LruCache<Long, Bitmap> =
        object : LruCache<Long, Bitmap>(24 * 1024 * 1024) {
            override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount
        }

    private val thumbCache: LruCache<Long, Bitmap> =
        object : LruCache<Long, Bitmap>(12 * 1024 * 1024) {
            override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount
        }

    // Songs we already know have no artwork, so we don't retry them
    private val withoutArt = mutableSetOf<Long>()

    fun cached(songId: Long): Bitmap? = bigCache.get(songId)

    fun cachedThumb(songId: Long): Bitmap? = thumbCache.get(songId)

    suspend fun load(context: Context, song: Song): Bitmap? {
        return loadInto(bigCache, context, song, BIG_SIZE)
    }

    suspend fun loadThumb(context: Context, song: Song): Bitmap? {
        return loadInto(thumbCache, context, song, THUMB_SIZE)
    }

    private fun knownMissing(songId: Long): Boolean {
        return synchronized(withoutArt) { withoutArt.contains(songId) }
    }

    private fun markMissing(songId: Long) {
        synchronized(withoutArt) { withoutArt.add(songId) }
    }

    private suspend fun loadInto(
        cache: LruCache<Long, Bitmap>,
        context: Context,
        song: Song,
        size: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val hit = cache.get(song.id)
        if (hit != null) return@withContext hit
        if (knownMissing(song.id)) return@withContext null

        val bitmap = fetch(context.applicationContext, song, size)
        if (bitmap != null) {
            cache.put(song.id, bitmap)
        } else {
            markMissing(song.id)
        }
        bitmap
    }

    private fun fetch(app: Context, song: Song, size: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val thumb = runCatching {
                app.contentResolver.loadThumbnail(song.uri, Size(size, size), null)
            }.getOrNull()
            if (thumb != null) return thumb
        }

        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(app, song.uri)
                val bytes = retriever.embeddedPicture
                if (bytes != null) decodeSampled(bytes, size) else null
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }

    // Decodes a smaller version of a big embedded picture to save memory
    private fun decodeSampled(bytes: ByteArray, target: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        var sample = 1
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        while (largest / (sample * 2) >= target) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}
package com.dawutdeveloper.trutho.data.media

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import com.dawutdeveloper.trutho.domain.Song
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.*
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey

object SongMetadataEditor {
    internal fun writeTags(edited: File, title: String, artist: String) {
        val audio = AudioFileIO.read(edited)
        val tag = audio.tagOrCreateAndSetDefault
        tag.setField(FieldKey.TITLE, title)
        tag.setField(FieldKey.ARTIST, artist)
        AudioFileIO.write(audio)
        val verified = AudioFileIO.read(edited).tag
        check(verified.getFirst(FieldKey.TITLE) == title && verified.getFirst(FieldKey.ARTIST) == artist) {
            "Could not verify the new metadata. The original file has not been changed."
        }
    }

    suspend fun save(context: Context, song: Song, title: String, artist: String): Song =
        withContext(Dispatchers.IO + NonCancellable) {
            val resolver = context.contentResolver
            val name = resolver.query(song.uri, arrayOf(MediaStore.Audio.Media.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: throw IOException("The audio file is no longer available")
            val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            require(extension in setOf("mp3", "m4a", "mp4", "m4b", "flac", "ogg", "oga", "wav", "wma", "aif", "aiff", "aifc", "dsf")) {
                "This file format does not support editing here. The original file has not been changed."
            }
            val original = File.createTempFile("original-", ".$extension", context.cacheDir)
            val edited = File.createTempFile("edited-", ".$extension", context.cacheDir)
            try {
                resolver.openInputStream(song.uri)?.use { input -> original.outputStream().use { input.copyTo(it) } }
                    ?: throw IOException("Cannot read the original audio file")
                original.copyTo(edited, overwrite = true)
                org.jaudiotagger.tag.TagOptionSingleton.getInstance().isAndroid = true
                writeTags(edited, title, artist)
                // Open before attempting a write: security exceptions must reach the permission flow.
                val descriptor = resolver.openFileDescriptor(song.uri, "rw")
                    ?: throw IOException("Cannot open the audio file for editing")
                descriptor.use { fd ->
                    android.os.ParcelFileDescriptor.AutoCloseOutputStream(fd).use { output ->
                        try {
                            output.channel.position(0)
                            edited.inputStream().use { it.copyTo(output) }
                            output.channel.truncate(edited.length())
                            output.fd.sync()
                        } catch (failure: Exception) {
                            // Restore the original bytes if copying the edited file fails.
                            try {
                                output.channel.position(0)
                                original.inputStream().use { it.copyTo(output) }
                                output.channel.truncate(original.length())
                                output.fd.sync()
                            } catch (restoreFailure: Exception) {
                                // Retain a recovery copy if storage itself has failed.
                                original.copyTo(File(context.filesDir, "audio-recovery-${song.id}.$extension"), true)
                                failure.addSuppressed(restoreFailure)
                            }
                            throw failure
                        }
                    }
                }
                // The embedded tags are already saved. Update Android's index as well.
                runCatching {
                    resolver.update(song.uri, ContentValues().apply {
                        put(MediaStore.Audio.Media.TITLE, title)
                        put(MediaStore.Audio.Media.ARTIST, artist)
                    }, null, null)
                }
                runCatching {
                    resolver.query(song.uri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0)?.let { path ->
                            android.media.MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ ->
                                MediaChanges.notifyChanged()
                            }
                        }
                    }
                }
                MediaChanges.notifyChanged()
                song.copy(title = title, artist = artist)
            } finally {
                original.delete()
                edited.delete()
            }
        }
}

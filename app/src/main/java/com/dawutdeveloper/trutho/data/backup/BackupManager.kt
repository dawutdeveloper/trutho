package com.dawutdeveloper.trutho.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.dawutdeveloper.trutho.data.local.AppDatabase
import com.dawutdeveloper.trutho.data.local.Folder
import com.dawutdeveloper.trutho.data.local.FolderSong
import com.dawutdeveloper.trutho.data.local.KaizenStatus
import com.dawutdeveloper.trutho.data.local.ListenLog
import com.dawutdeveloper.trutho.data.local.Note
import com.dawutdeveloper.trutho.data.local.TrackState
import com.dawutdeveloper.trutho.data.media.SongScanner
import com.dawutdeveloper.trutho.domain.Song
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object BackupManager {

    private const val APP_ID = "trutho"

    // Backups made before the rename used this id and must still be accepted
    private const val LEGACY_APP_ID = "voicesofheart"
    private const val FORMAT_VERSION = 2

    // Files re-encoded or re-tagged can differ slightly in length
    private const val MATCH_TOLERANCE_MS = 3000L

    class ImportSummary(
        val notesAdded: Int,
        val statusesRestored: Int,
        val foldersAdded: Int,
        val folderSongsAdded: Int,
        val listensAdded: Int,
        val songsNotFound: Int
    )

    private class BackupSong(
        val title: String,
        val artist: String,
        val duration: Long
    )

    // Saves everything to the file the user picked. Returns how many records were saved.
    suspend fun exportTo(context: Context, uri: Uri): Result<Int> =
        withContext(Dispatchers.IO + NonCancellable) {
            runCatching {
                val app = context.applicationContext
                val db = AppDatabase.getInstance(app)

                val notes = db.noteDao().getAll()
                val states = db.trackStateDao().getAll()
                val folders = db.folderDao().getAllFolders()
                val links = db.folderDao().getAllLinks()
                val logs = db.listenLogDao().getAll()
                val favorites = db.favoriteSongDao().getAll()

                val songs = runCatching { SongScanner(app).scanSongs() }
                    .getOrDefault(emptyList())
                val songById = songs.associateBy { it.id }

                // Every song that appears in your data
                val usedIds = HashSet<Long>()
                for (favorite in favorites) usedIds.add(favorite.songId)
                for (n in notes) usedIds.add(n.songId)
                for (s in states) usedIds.add(s.songId)
                for (l in links) usedIds.add(l.songId)
                for (g in logs) usedIds.add(g.songId)

                val songsJson = JSONArray()
                for (id in usedIds) {
                    val song = songById[id]
                    if (song != null) {
                        val o = JSONObject()
                        o.put("id", id)
                        o.put("title", song.title)
                        o.put("artist", song.artist)
                        o.put("duration", song.duration)
                        songsJson.put(o)
                    }
                }

                val notesJson = JSONArray()
                for (n in notes) {
                    val o = JSONObject()
                    o.put("songId", n.songId)
                    o.put("text", n.text)
                    o.put("isFavorite", n.isFavorite)
                    o.put("createdAt", n.createdAt)
                    val ms = n.timestampMs
                    if (ms != null) o.put("timestampMs", ms)
                    notesJson.put(o)
                }

                val statesJson = JSONArray()
                for (s in states) {
                    val o = JSONObject()
                    o.put("songId", s.songId)
                    o.put("status", s.status.name)
                    o.put("targetRepeats", s.targetRepeats)
                    o.put("completedRepeats", s.completedRepeats)
                    o.put("updatedAt", s.updatedAt)
                    statesJson.put(o)
                }

                val foldersJson = JSONArray()
                for (f in folders) {
                    val o = JSONObject()
                    o.put("id", f.id)
                    o.put("name", f.name)
                    o.put("createdAt", f.createdAt)
                    foldersJson.put(o)
                }

                val linksJson = JSONArray()
                for (l in links) {
                    val o = JSONObject()
                    o.put("folderId", l.folderId)
                    o.put("songId", l.songId)
                    o.put("addedAt", l.addedAt)
                    linksJson.put(o)
                }

                val logsJson = JSONArray()
                for (g in logs) {
                    val o = JSONObject()
                    o.put("songId", g.songId)
                    o.put("listenedAt", g.listenedAt)
                    logsJson.put(o)
                }

                val root = JSONObject()
                root.put("app", APP_ID)
                root.put("version", FORMAT_VERSION)
                root.put("exportedAt", System.currentTimeMillis())
                root.put("songs", songsJson)
                root.put("notes", notesJson)
                root.put("trackStates", statesJson)
                root.put("folders", foldersJson)
                root.put("folderSongs", linksJson)
                root.put("listenLog", logsJson)
                root.put("favoriteSongs", JSONArray(favorites.map { it.songId }))

                val bytes = root.toString().toByteArray(Charsets.UTF_8)
                val stream = app.contentResolver.openOutputStream(uri, "wt")
                    ?: error("Could not open the file for writing")
                stream.use { it.write(bytes) }

                notes.size + states.size + folders.size + links.size + logs.size + favorites.size
            }
        }

    // Merges a backup file into the app. Nothing that already exists is deleted or duplicated.
    suspend fun importFrom(context: Context, uri: Uri): Result<ImportSummary> =
        withContext(Dispatchers.IO + NonCancellable) {
            runCatching {
                val app = context.applicationContext
                val db = AppDatabase.getInstance(app)

                val input = app.contentResolver.openInputStream(uri)
                    ?: error("Could not open the file")
                val text = input.use { it.readBytes().toString(Charsets.UTF_8) }
                val root = JSONObject(text)

                val backupAppId = root.optString("app")
                if (backupAppId != APP_ID && backupAppId != LEGACY_APP_ID) {
                    error("This file is not a Trutho backup")
                }
                if (root.optInt("version", 0) > FORMAT_VERSION) {
                    error("This backup was made by a newer version of the app")
                }

                // Songs as they were described in the backup
                val backupSongs = HashMap<Long, BackupSong>()
                val songsArr = root.optJSONArray("songs")
                if (songsArr != null) {
                    for (i in 0 until songsArr.length()) {
                        val o = songsArr.getJSONObject(i)
                        backupSongs[o.getLong("id")] = BackupSong(
                            o.getString("title"),
                            o.getString("artist"),
                            o.getLong("duration")
                        )
                    }
                }

                // Songs that exist on this phone now
                val current: List<Song> = runCatching { SongScanner(app).scanSongs() }
                    .getOrDefault(emptyList())
                val currentById = current.associateBy { it.id }

                val idMap = HashMap<Long, Long>()
                val missing = HashSet<Long>()

                // Finds the song on this phone that matches an old song id
                fun resolve(oldId: Long): Long? {
                    val cached = idMap[oldId]
                    if (cached != null) return if (cached == -1L) null else cached

                    val found = findSong(backupSongs[oldId], oldId, current, currentById)
                    idMap[oldId] = found ?: -1L
                    if (found == null) missing.add(oldId)
                    return found
                }

                var notesAdded = 0
                var statusesRestored = 0
                var foldersAdded = 0
                var folderSongsAdded = 0
                var listensAdded = 0

                db.withTransaction {
                    val favoriteIds = root.optJSONArray("favoriteSongs")
                    if (favoriteIds != null) {
                        for (i in 0 until favoriteIds.length()) {
                            resolve(favoriteIds.getLong(i))?.let {
                                db.favoriteSongDao().add(com.dawutdeveloper.trutho.data.local.FavoriteSong(it))
                            }
                        }
                    }
                    // ---- Notes ----
                    val noteKeys = HashSet<String>()
                    for (n in db.noteDao().getAll()) {
                        noteKeys.add("${n.songId}|${n.createdAt}|${n.timestampMs}|${n.text}")
                    }
                    val notesToInsert = ArrayList<Note>()
                    val notesArr = root.optJSONArray("notes")
                    if (notesArr != null) {
                        for (i in 0 until notesArr.length()) {
                            val o = notesArr.getJSONObject(i)
                            val newSong = resolve(o.getLong("songId"))
                            if (newSong == null) continue

                            val noteText = o.getString("text")
                            val createdAt = o.getLong("createdAt")
                            val stamp = if (o.has("timestampMs") && !o.isNull("timestampMs")) {
                                o.getLong("timestampMs")
                            } else {
                                null
                            }

                            if (noteKeys.add("$newSong|$createdAt|$stamp|$noteText")) {
                                notesToInsert.add(
                                    Note(
                                        songId = newSong,
                                        text = noteText,
                                        createdAt = createdAt,
                                        timestampMs = stamp,
                                        isFavorite = o.optBoolean("isFavorite", false)
                                    )
                                )
                            }
                        }
                    }
                    if (notesToInsert.isNotEmpty()) db.noteDao().insertAll(notesToInsert)
                    notesAdded = notesToInsert.size

                    // ---- Kaizen statuses (the newer one wins) ----
                    val existingStates = db.trackStateDao().getAll().associateBy { it.songId }
                    val statesArr = root.optJSONArray("trackStates")
                    if (statesArr != null) {
                        for (i in 0 until statesArr.length()) {
                            val o = statesArr.getJSONObject(i)
                            val newSong = resolve(o.getLong("songId"))
                            if (newSong == null) continue

                            val status = runCatching {
                                KaizenStatus.valueOf(o.getString("status"))
                            }.getOrNull()
                            if (status == null) continue

                            val incoming = TrackState(
                                songId = newSong,
                                status = status,
                                targetRepeats = o.getInt("targetRepeats"),
                                completedRepeats = o.getInt("completedRepeats"),
                                updatedAt = o.getLong("updatedAt")
                            )
                            val existing = existingStates[newSong]
                            if (existing == null || incoming.updatedAt > existing.updatedAt) {
                                db.trackStateDao().upsert(incoming)
                                statusesRestored++
                            }
                        }
                    }

                    // ---- Folders (matched by name) ----
                    val folderIdByName = HashMap<String, Long>()
                    for (f in db.folderDao().getAllFolders()) {
                        folderIdByName[f.name.trim().lowercase()] = f.id
                    }
                    val folderIdMap = HashMap<Long, Long>()
                    val foldersArr = root.optJSONArray("folders")
                    if (foldersArr != null) {
                        for (i in 0 until foldersArr.length()) {
                            val o = foldersArr.getJSONObject(i)
                            val oldId = o.getLong("id")
                            val name = o.getString("name")
                            val key = name.trim().lowercase()

                            val existingId = folderIdByName[key]
                            if (existingId != null) {
                                folderIdMap[oldId] = existingId
                            } else {
                                val createdId = db.folderDao().insertFolder(
                                    Folder(name = name, createdAt = o.getLong("createdAt"))
                                )
                                folderIdByName[key] = createdId
                                folderIdMap[oldId] = createdId
                                foldersAdded++
                            }
                        }
                    }

                    // ---- Songs inside folders ----
                    val linkKeys = HashSet<String>()
                    for (l in db.folderDao().getAllLinks()) {
                        linkKeys.add("${l.folderId}|${l.songId}")
                    }
                    val linksArr = root.optJSONArray("folderSongs")
                    if (linksArr != null) {
                        for (i in 0 until linksArr.length()) {
                            val o = linksArr.getJSONObject(i)
                            val newFolder = folderIdMap[o.getLong("folderId")]
                            if (newFolder == null) continue
                            val newSong = resolve(o.getLong("songId"))
                            if (newSong == null) continue

                            if (linkKeys.add("$newFolder|$newSong")) {
                                db.folderDao().addSong(
                                    FolderSong(
                                        folderId = newFolder,
                                        songId = newSong,
                                        addedAt = o.getLong("addedAt")
                                    )
                                )
                                folderSongsAdded++
                            }
                        }
                    }

                    // ---- Listening history ----
                    val logKeys = HashSet<String>()
                    for (g in db.listenLogDao().getAll()) {
                        logKeys.add("${g.songId}|${g.listenedAt}")
                    }
                    val logsToInsert = ArrayList<ListenLog>()
                    val logsArr = root.optJSONArray("listenLog")
                    if (logsArr != null) {
                        for (i in 0 until logsArr.length()) {
                            val o = logsArr.getJSONObject(i)
                            val newSong = resolve(o.getLong("songId"))
                            if (newSong == null) continue

                            val listenedAt = o.getLong("listenedAt")
                            if (logKeys.add("$newSong|$listenedAt")) {
                                logsToInsert.add(ListenLog(songId = newSong, listenedAt = listenedAt))
                            }
                        }
                    }
                    if (logsToInsert.isNotEmpty()) db.listenLogDao().insertAll(logsToInsert)
                    listensAdded = logsToInsert.size
                }

                ImportSummary(
                    notesAdded = notesAdded,
                    statusesRestored = statusesRestored,
                    foldersAdded = foldersAdded,
                    folderSongsAdded = folderSongsAdded,
                    listensAdded = listensAdded,
                    songsNotFound = missing.size
                )
            }
        }

    private fun findSong(
        backup: BackupSong?,
        oldId: Long,
        current: List<Song>,
        currentById: Map<Long, Song>
    ): Long? {
        // The backup has no description of this song: only the same id on the same phone can match
        if (backup == null) {
            return if (currentById.containsKey(oldId)) oldId else null
        }

        // Same phone: the same id, and it is really the same song
        val sameId = currentById[oldId]
        if (sameId != null &&
            sameId.title.equals(backup.title, ignoreCase = true) &&
            sameId.artist.equals(backup.artist, ignoreCase = true) &&
            abs(sameId.duration - backup.duration) <= MATCH_TOLERANCE_MS
        ) {
            return sameId.id
        }

        // New phone or reinstall: match by title, artist and length
        val candidates = current.filter {
            it.title.equals(backup.title, ignoreCase = true) &&
                    it.artist.equals(backup.artist, ignoreCase = true)
        }
        val best = candidates.minByOrNull { abs(it.duration - backup.duration) }
        return if (best != null && abs(best.duration - backup.duration) <= MATCH_TOLERANCE_MS) {
            best.id
        } else {
            null
        }
    }
}
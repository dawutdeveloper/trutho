package com.example.voicesofheart.data.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.voicesofheart.data.local.AppDatabase
import com.example.voicesofheart.data.local.KaizenStatus
import com.example.voicesofheart.data.media.SongScanner
import com.example.voicesofheart.domain.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.voicesofheart.data.local.ListenLog

object PlayerManager {

    private const val PREFS_NAME = "player_state"
    private const val KEY_SONG_ID = "songId"
    private const val KEY_POSITION = "position"
    private const val KEY_QUEUE = "queue"

    private class SavedState(
        val songId: Long,
        val positionMs: Long,
        val queueIds: List<Long>
    )

    private var controller: MediaController? = null
    private var connecting = false
    private var appContext: Context? = null

    // Actions requested before the service connection is ready
    private val pendingActions = mutableListOf<(MediaController) -> Unit>()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private var playlist: List<Song> = emptyList()
    private var endedHandled = false

    // True once the user started something, so a late restore never overrides it
    private var restoreCancelled = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startTicker()
            } else {
                stopTicker()
                saveState()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // The last song of the list finished
            if (playbackState == Player.STATE_ENDED && !endedHandled) {
                endedHandled = true
                _currentSong.value?.let { recordCompletion(it) }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val previous = _currentSong.value
            // A song finished by itself and the next one started
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && previous != null) {
                recordCompletion(previous)
            }
            endedHandled = false
            val match = playlist.firstOrNull { it.uri.toString() == mediaItem?.mediaId }
            if (match != null) {
                _currentSong.value = match
                saveState()
            }
        }
    }

    fun init(context: Context) {
        if (controller != null || connecting) return
        connecting = true

        val app = context.applicationContext
        appContext = app

        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener(
            {
                val connected = runCatching { future.get() }.getOrNull()
                connecting = false
                if (connected != null) {
                    controller = connected
                    connected.addListener(playerListener)
                    _isPlaying.value = connected.isPlaying
                    if (connected.isPlaying) startTicker()
                    pendingActions.forEach { action -> action(connected) }
                    pendingActions.clear()
                    restoreIfNeeded(connected)
                }
            },
            ContextCompat.getMainExecutor(app)
        )
    }

    private fun Song.toMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(uri.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .build()
            )
            .build()
    }

    fun playSong(song: Song, songList: List<Song> = listOf(song)) {
        restoreCancelled = true
        playlist = songList
        endedHandled = false
        _currentSong.value = song

        val startIndex = songList.indexOf(song).coerceAtLeast(0)
        val mediaItems = songList.map { it.toMediaItem() }

        val action: (MediaController) -> Unit = { c ->
            c.setMediaItems(mediaItems, startIndex, 0L)
            c.prepare()
            c.play()
        }

        val c = controller
        if (c != null) action(c) else pendingActions.add(action)
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun skipNext() {
        controller?.seekToNext()
    }

    fun skipPrevious() {
        controller?.seekToPrevious()
    }

    fun currentPosition(): Long = controller?.currentPosition ?: 0L

    fun duration(): Long = controller?.duration?.takeIf { it > 0 } ?: 0L

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    // ---------- Saving and restoring the last session ----------

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = mainScope.launch {
            while (isActive) {
                delay(5000)
                saveState()
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    // Must be called on the main thread (all current callers are)
    fun saveState() {
        val app = appContext ?: return
        val c = controller ?: return
        val song = _currentSong.value ?: return
        if (c.mediaItemCount == 0 || playlist.isEmpty()) return

        val duration = c.duration
        val finished = c.playbackState == Player.STATE_ENDED ||
                (duration > 0 && c.currentPosition >= duration - 1000)
        val position = if (finished) 0L else c.currentPosition.coerceAtLeast(0L)

        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_SONG_ID, song.id)
            .putLong(KEY_POSITION, position)
            .putString(KEY_QUEUE, playlist.joinToString(",") { it.id.toString() })
            .apply()
    }

    private fun readSavedState(): SavedState? {
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: return null
        val songId = prefs.getLong(KEY_SONG_ID, -1L)
        if (songId == -1L) return null
        val ids = prefs.getString(KEY_QUEUE, "").orEmpty()
            .split(",")
            .mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) return null
        return SavedState(songId, prefs.getLong(KEY_POSITION, 0L), ids)
    }

    private fun restoreIfNeeded(c: MediaController) {
        // Music is already loaded (still playing in the background): do nothing
        if (c.mediaItemCount > 0) return
        val app = appContext ?: return
        val saved = readSavedState() ?: return

        mainScope.launch {
            val all = withContext(Dispatchers.IO) {
                runCatching { SongScanner(app).scanSongs() }.getOrDefault(emptyList())
            }
            // The user may have started something else while we were scanning
            if (restoreCancelled || c.mediaItemCount > 0) return@launch

            val byId = all.associateBy { it.id }
            val queue = saved.queueIds.mapNotNull { byId[it] }
            val index = queue.indexOfFirst { it.id == saved.songId }
            if (index < 0) return@launch // the saved song no longer exists

            playlist = queue
            endedHandled = false
            _currentSong.value = queue[index]

            // Load the queue at the saved position, but stay paused
            c.setMediaItems(queue.map { it.toMediaItem() }, index, saved.positionMs)
            c.prepare()
        }
    }

    // ---------- Kaizen auto-counting ----------

    private fun recordCompletion(song: Song) {
        val ctx = appContext ?: return
        ioScope.launch {
            val db = AppDatabase.getInstance(ctx)

            // Every full listen goes into the listening log
            db.listenLogDao().insert(ListenLog(songId = song.id))

            val dao = db.trackStateDao()
            val state = dao.get(song.id) ?: return@launch
            val now = System.currentTimeMillis()

            val updated = when (state.status) {
                KaizenStatus.REPEAT -> {
                    val done = state.completedRepeats + 1
                    if (done >= state.targetRepeats) {
                        state.copy(
                            status = KaizenStatus.LISTENED,
                            completedRepeats = done,
                            updatedAt = now
                        )
                    } else {
                        state.copy(completedRepeats = done, updatedAt = now)
                    }
                }
                KaizenStatus.MUST_LISTEN,
                KaizenStatus.CURRENTLY_LISTENING ->
                    state.copy(status = KaizenStatus.LISTENED, updatedAt = now)
                KaizenStatus.LISTENED -> state
            }
            if (updated != state) dao.upsert(updated)
        }
    }

    fun release() {
        stopTicker()
        controller?.release()
        controller = null
        connecting = false
    }
}
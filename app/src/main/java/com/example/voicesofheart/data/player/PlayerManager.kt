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
import com.example.voicesofheart.domain.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object PlayerManager {

    private var controller: MediaController? = null
    private var connecting = false
    private var appContext: Context? = null

    // Actions requested before the service connection is ready
    private val pendingActions = mutableListOf<(MediaController) -> Unit>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private var playlist: List<Song> = emptyList()
    private var endedHandled = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
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
            if (match != null) _currentSong.value = match
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
                    pendingActions.forEach { action -> action(connected) }
                    pendingActions.clear()
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

    private fun recordCompletion(song: Song) {
        val ctx = appContext ?: return
        scope.launch {
            val dao = AppDatabase.getInstance(ctx).trackStateDao()
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
        controller?.release()
        controller = null
        connecting = false
    }
}
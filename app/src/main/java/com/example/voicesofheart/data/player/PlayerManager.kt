package com.example.voicesofheart.data.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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

    private var exoPlayer: ExoPlayer? = null
    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private var playlist: List<Song> = emptyList()
    private var endedHandled = false

    fun init(context: Context) {
        if (exoPlayer != null) return
        appContext = context.applicationContext
        exoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
            addListener(object : Player.Listener {
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
            })
        }
    }

    fun playSong(song: Song, songList: List<Song> = listOf(song)) {
        playlist = songList
        endedHandled = false
        val startIndex = songList.indexOf(song).coerceAtLeast(0)
        val mediaItems = songList.map {
            MediaItem.Builder().setUri(it.uri).setMediaId(it.uri.toString()).build()
        }
        exoPlayer?.apply {
            setMediaItems(mediaItems, startIndex, 0L)
            prepare()
            play()
        }
        _currentSong.value = song
    }

    fun togglePlayPause() {
        exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun skipNext() = exoPlayer?.seekToNext()
    fun skipPrevious() = exoPlayer?.seekToPrevious()

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
        exoPlayer?.release()
        exoPlayer = null
    }
}
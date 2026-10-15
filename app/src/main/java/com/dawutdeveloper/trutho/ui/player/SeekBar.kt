package com.dawutdeveloper.trutho.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dawutdeveloper.trutho.data.player.PlayerManager
import kotlinx.coroutines.delay

@Composable
fun SeekBar(modifier: Modifier = Modifier) {
    val currentSong by PlayerManager.currentSong.collectAsState()
    val isPlaying by PlayerManager.isPlaying.collectAsState()

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    // Refresh the position twice a second (stops automatically when the screen closes)
    LaunchedEffect(currentSong, isPlaying) {
        while (true) {
            if (!dragging) {
                position = PlayerManager.currentPosition()
                duration = PlayerManager.duration()
            }
            delay(500)
        }
    }

    val max = duration.coerceAtLeast(1L).toFloat()
    val shown = if (dragging) dragValue else position.toFloat()

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = shown.coerceIn(0f, max),
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                PlayerManager.seekTo(dragValue.toLong())
                position = dragValue.toLong()
                dragging = false
            },
            valueRange = 0f..max
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(shown.toLong()), style = MaterialTheme.typography.labelSmall)
            Text(formatTime(duration), style = MaterialTheme.typography.labelSmall)
        }
    }
}
package com.example.voicesofheart.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.voicesofheart.data.local.AppDatabase
import com.example.voicesofheart.data.local.KaizenStatus
import com.example.voicesofheart.data.media.SongScanner
import com.example.voicesofheart.data.player.PlayerManager
import com.example.voicesofheart.domain.Song
import com.example.voicesofheart.ui.components.SongRow
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun startOfTodayMillis(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

@Composable
fun HomeScreen(onOpenPlayer: (Long) -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }

    val statesFlow = remember { db.trackStateDao().observeAll() }
    val states by statesFlow.collectAsState(initial = emptyList())

    val startOfToday = remember { startOfTodayMillis() }
    val todayFlow = remember(startOfToday) { db.listenLogDao().observeCountSince(startOfToday) }
    val listensToday by todayFlow.collectAsState(initial = 0)

    val currentSong by PlayerManager.currentSong.collectAsState()
    val isPlaying by PlayerManager.isPlaying.collectAsState()

    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    LaunchedEffect(Unit) {
        songs = withContext(Dispatchers.IO) {
            runCatching { SongScanner(context).scanSongs() }.getOrDefault(emptyList())
        }
    }

    val songById = songs.associateBy { it.id }
    val countByStatus = states.groupingBy { it.status }.eachCount()
    val mustListenSongs = states
        .filter { it.status == KaizenStatus.MUST_LISTEN }
        .sortedBy { it.updatedAt }
        .mapNotNull { state -> songById[state.songId] }

    val todayText = when (listensToday) {
        0 -> "No full listens yet today"
        1 -> "1 full listen today"
        else -> "$listensToday full listens today"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                Text("Voices of Heart", style = MaterialTheme.typography.headlineSmall)
                Text(
                    todayText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            val song = currentSong
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .clickable(enabled = song != null) { song?.let { onOpenPlayer(it.id) } }
            ) {
                if (song != null) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Continue listening",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                song.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                song.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FilledIconButton(onClick = { PlayerManager.togglePlayPause() }) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play"
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Nothing playing yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Pick a song in the Library to begin.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                KaizenStatus.entries.forEach { status ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "${countByStatus[status] ?: 0}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            status.label,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        item {
            Text(
                "Must listen",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
            )
        }

        if (mustListenSongs.isEmpty()) {
            item {
                Text(
                    "Nothing queued. Mark songs as Must listen from the player screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            items(mustListenSongs.take(5), key = { it.id }) { song ->
                SongRow(
                    song = song,
                    isCurrent = currentSong?.id == song.id,
                    isPlaying = isPlaying,
                    onClick = { PlayerManager.playSong(song, mustListenSongs) }
                )
                HorizontalDivider()
            }
        }
    }
}
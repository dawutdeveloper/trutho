package com.example.voicesofheart.ui.kaizen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.unit.dp
import com.example.voicesofheart.data.local.AppDatabase
import com.example.voicesofheart.data.local.KaizenStatus
import com.example.voicesofheart.data.media.SongScanner
import com.example.voicesofheart.data.player.PlayerManager
import com.example.voicesofheart.domain.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun KaizenScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val states by db.trackStateDao().observeAll().collectAsState(initial = emptyList())

    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    LaunchedEffect(Unit) {
        songs = withContext(Dispatchers.IO) { SongScanner(context).scanSongs() }
    }

    var selected by remember { mutableStateOf(KaizenStatus.MUST_LISTEN) }

    val songById = songs.associateBy { it.id }
    val items = states
        .filter { it.status == selected }
        .mapNotNull { state -> songById[state.songId]?.let { song -> song to state } }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KaizenStatus.entries.forEach { status ->
                val count = states.count { it.status == status }
                FilterChip(
                    selected = selected == status,
                    onClick = { selected = status },
                    label = { Text("${status.label} ($count)") }
                )
            }
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing here yet", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items) { (song, state) ->
                    ListItem(
                        headlineContent = {
                            Text(song.title, style = MaterialTheme.typography.titleMedium)
                        },
                        supportingContent = {
                            val extra = if (state.status == KaizenStatus.REPEAT) {
                                "  |  ${state.completedRepeats} of ${state.targetRepeats}"
                            } else ""
                            Text(song.artist + extra, style = MaterialTheme.typography.bodyMedium)
                        },
                        modifier = Modifier.clickable {
                            PlayerManager.playSong(song, items.map { it.first })
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
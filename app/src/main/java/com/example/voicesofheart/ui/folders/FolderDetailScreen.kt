package com.example.voicesofheart.ui.folders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.voicesofheart.data.local.AppDatabase
import com.example.voicesofheart.data.media.SongScanner
import com.example.voicesofheart.data.player.PlayerManager
import com.example.voicesofheart.domain.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FolderDetailScreen(folderId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    val folderFlow = remember(folderId) { db.folderDao().observeFolder(folderId) }
    val idsFlow = remember(folderId) { db.folderDao().observeSongIds(folderId) }
    val folder by folderFlow.collectAsState(initial = null)
    val songIds by idsFlow.collectAsState(initial = emptyList())

    var allSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    LaunchedEffect(Unit) {
        allSongs = withContext(Dispatchers.IO) { SongScanner(context).scanSongs() }
    }

    val songById = allSongs.associateBy { it.id }
    val songs = songIds.mapNotNull { songById[it] }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                folder?.name ?: "",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (songs.isNotEmpty()) {
                FilledTonalButton(onClick = { PlayerManager.playSong(songs.first(), songs) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Play all")
                }
            }
        }

        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No songs here yet. Add songs from the Library tab.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(songs, key = { it.id }) { song ->
                    ListItem(
                        headlineContent = {
                            Text(song.title, style = MaterialTheme.typography.titleMedium)
                        },
                        supportingContent = {
                            Text(song.artist, style = MaterialTheme.typography.bodyMedium)
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                scope.launch { db.folderDao().removeSong(folderId, song.id) }
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove from folder")
                            }
                        },
                        modifier = Modifier.clickable { PlayerManager.playSong(song, songs) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
package com.dawutdeveloper.trutho.ui.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dawutdeveloper.trutho.data.local.AppDatabase
import com.dawutdeveloper.trutho.data.media.SongScanner
import com.dawutdeveloper.trutho.data.player.PlayerManager
import com.dawutdeveloper.trutho.domain.Song
import com.dawutdeveloper.trutho.ui.components.*
import com.dawutdeveloper.trutho.ui.notes.NotesScreen
import kotlinx.coroutines.*

@Composable
fun FavoritesScreen(onBack: () -> Unit, onOpenNote: (Int) -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val ids by remember { db.favoriteSongDao().observeIds() }.collectAsState(initial = emptyList())
    val current by PlayerManager.currentSong.collectAsState()
    val playing by PlayerManager.isPlaying.collectAsState()
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var notesTab by rememberSaveable { mutableStateOf(false) }
    val mediaRevision by com.dawutdeveloper.trutho.data.media.MediaChanges.revision.collectAsState()
    LaunchedEffect(mediaRevision) {
        songs = withContext(Dispatchers.IO) { runCatching { SongScanner(context).scanSongs() }.getOrDefault(emptyList()) }
    }
    val favorites = songs.filter { it.id in ids }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp)) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Favorites", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(8.dp))
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !notesTab, onClick = { notesTab = false }, label = { Text("Songs") })
            FilterChip(selected = notesTab, onClick = { notesTab = true }, label = { Text("Notes") })
        }
        if (notesTab) NotesScreen(onOpenNote, favoritesOnly = true)
        else if (favorites.isEmpty()) Text("No favorite songs yet. Tap the heart in the player.", Modifier.padding(24.dp))
        else LazyColumn(Modifier.weight(1f)) {
            items(favorites, key = { it.id }) { song ->
                SongRow(song = song, isCurrent = current?.id == song.id, isPlaying = playing,
                    onClick = { PlayerManager.playSong(song, favorites) },
                    trailing = { FavoriteButton(true, { scope.launch { db.favoriteSongDao().remove(song.id) } }) })
            }
        }
    }
}

package com.example.voicesofheart.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.voicesofheart.data.local.AppDatabase
import com.example.voicesofheart.data.local.Note
import com.example.voicesofheart.data.player.PlayerManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(songId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    val currentSong by PlayerManager.currentSong.collectAsState()
    val isPlaying by PlayerManager.isPlaying.collectAsState()

    var showNotes by remember { mutableStateOf(false) }
    val notes by db.noteDao().getNotesForSong(songId).collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = { showNotes = true }) {
                        Icon(Icons.Filled.EditNote, contentDescription = "Notes")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                currentSong?.title ?: "No song",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                currentSong?.artist ?: "",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(32.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { PlayerManager.skipPrevious() }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.width(16.dp))
                FilledIconButton(onClick = { PlayerManager.togglePlayPause() }, modifier = Modifier.size(64.dp)) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                IconButton(onClick = { PlayerManager.skipNext() }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
                }
            }
        }
    }

    if (showNotes) {
        ModalBottomSheet(onDismissRequest = { showNotes = false }) {
            NotesSheetContent(
                notes = notes,
                onAddNote = { text ->
                    scope.launch {
                        db.noteDao().insertNote(Note(songId = songId, text = text))
                    }
                },
                onDeleteNote = { note ->
                    scope.launch { db.noteDao().deleteNote(note) }
                }
            )
        }
    }
}

@Composable
private fun NotesSheetContent(
    notes: List<Note>,
    onAddNote: (String) -> Unit,
    onDeleteNote: (Note) -> Unit
) {
    var text by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Notes", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Write a note about this song...") }
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {
                if (text.isNotBlank()) {
                    onAddNote(text.trim())
                    text = ""
                }
            }) {
                Icon(Icons.Filled.Send, contentDescription = "Save note")
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
            items(notes) { note ->
                ListItem(
                    headlineContent = { Text(note.text) },
                    trailingContent = {
                        IconButton(onClick = { onDeleteNote(note) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete note")
                        }
                    }
                )
                HorizontalDivider()
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
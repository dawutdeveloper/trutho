package com.example.voicesofheart.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.voicesofheart.data.local.KaizenStatus
import com.example.voicesofheart.data.local.Note
import com.example.voicesofheart.data.local.TrackState
import com.example.voicesofheart.data.player.PlayerManager
import com.example.voicesofheart.ui.components.SongArtwork
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(songId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    val currentSong by PlayerManager.currentSong.collectAsState()
    val isPlaying by PlayerManager.isPlaying.collectAsState()

    // Follow the song that is really playing (it can change with next/previous)
    val activeId = currentSong?.id ?: songId

    var showNotes by remember { mutableStateOf(false) }
    val notesFlow = remember(activeId) { db.noteDao().getNotesForSong(activeId) }
    val kaizenFlow = remember(activeId) { db.trackStateDao().observe(activeId) }
    val notes by notesFlow.collectAsState(initial = emptyList())
    val kaizen by kaizenFlow.collectAsState(initial = null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        // Top row: Notes on the left, close on the right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showNotes = true }) {
                Icon(Icons.Filled.EditNote, contentDescription = "Notes")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close")
            }
        }

        // Cover: the biggest square that fits in the free space
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val side = minOf(maxWidth, maxHeight)
            SongArtwork(
                song = currentSong,
                modifier = Modifier.size(side)
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                currentSong?.title ?: "Nothing playing",
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                currentSong?.artist ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(20.dp))
        SeekBar(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { PlayerManager.skipPrevious() },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(32.dp)
                )
            }
            FilledIconButton(
                onClick = { PlayerManager.togglePlayPause() },
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(36.dp)
                )
            }
            IconButton(
                onClick = { PlayerManager.skipNext() },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        KaizenControls(
            state = kaizen,
            onSelect = { status ->
                scope.launch {
                    val base = kaizen ?: TrackState(songId = activeId, status = status)
                    db.trackStateDao().upsert(
                        base.copy(
                            status = status,
                            completedRepeats = if (status == KaizenStatus.REPEAT &&
                                base.status != KaizenStatus.REPEAT
                            ) {
                                0
                            } else {
                                base.completedRepeats
                            },
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            },
            onTargetChange = { target ->
                kaizen?.let { current ->
                    scope.launch {
                        db.trackStateDao().upsert(
                            current.copy(targetRepeats = target.coerceIn(2, 4))
                        )
                    }
                }
            }
        )

        Spacer(Modifier.height(16.dp))
    }

    if (showNotes) {
        ModalBottomSheet(onDismissRequest = { showNotes = false }) {
            NotesSheetContent(
                notes = notes,
                onAddNote = { text, stampMs ->
                    scope.launch {
                        db.noteDao().insertNote(
                            Note(songId = activeId, text = text, timestampMs = stampMs)
                        )
                    }
                },
                onDeleteNote = { note ->
                    scope.launch { db.noteDao().deleteNote(note) }
                },
                onJump = { ms ->
                    PlayerManager.seekTo(ms)
                    if (!isPlaying) PlayerManager.togglePlayPause()
                    showNotes = false
                }
            )
        }
    }
}

@Composable
private fun KaizenControls(
    state: TrackState?,
    onSelect: (KaizenStatus) -> Unit,
    onTargetChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KaizenStatus.entries.forEach { status ->
                FilterChip(
                    selected = state?.status == status,
                    onClick = { onSelect(status) },
                    label = { Text(status.label) }
                )
            }
        }

        if (state != null && state.status == KaizenStatus.REPEAT) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onTargetChange(state.targetRepeats - 1) },
                    enabled = state.targetRepeats > 2
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Fewer repeats")
                }
                Text(
                    "Repeat ${state.completedRepeats} of ${state.targetRepeats}",
                    style = MaterialTheme.typography.bodyMedium
                )
                IconButton(
                    onClick = { onTargetChange(state.targetRepeats + 1) },
                    enabled = state.targetRepeats < 4
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "More repeats")
                }
            }
        }
    }
}

@Composable
private fun NotesSheetContent(
    notes: List<Note>,
    onAddNote: (String, Long?) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onJump: (Long) -> Unit
) {
    var text by remember { mutableStateOf("") }
    // The time is captured when the sheet opens, which is the moment you wanted to note
    var stampMs by remember { mutableStateOf<Long?>(PlayerManager.currentPosition()) }
    val stamp = stampMs

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text("Notes", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                placeholder = { Text("Write a note about this song...") }
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {
                if (text.isNotBlank()) {
                    onAddNote(text.trim(), stamp)
                    text = ""
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Save note")
            }
        }

        Spacer(Modifier.height(8.dp))

        FilterChip(
            selected = stamp != null,
            onClick = {
                stampMs = if (stamp == null) PlayerManager.currentPosition() else null
            },
            label = {
                Text(if (stamp != null) "At ${formatTime(stamp)}" else "No timestamp")
            }
        )

        Spacer(Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
            items(notes, key = { it.id }) { note ->
                val ms = note.timestampMs
                val timeLabel: (@Composable () -> Unit)? = if (ms != null) {
                    {
                        Text(
                            formatTime(ms),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    null
                }

                ListItem(
                    leadingContent = timeLabel,
                    headlineContent = { Text(note.text) },
                    trailingContent = {
                        IconButton(onClick = { onDeleteNote(note) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete note")
                        }
                    },
                    modifier = if (ms != null) {
                        Modifier.clickable { onJump(ms) }
                    } else {
                        Modifier
                    }
                )
                HorizontalDivider()
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
package com.dawutdeveloper.trutho.ui.notes

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dawutdeveloper.trutho.data.local.AppDatabase
import com.dawutdeveloper.trutho.data.media.SongScanner
import com.dawutdeveloper.trutho.data.player.PlayerManager
import com.dawutdeveloper.trutho.domain.Song
import com.dawutdeveloper.trutho.ui.components.SongThumbnail
import com.dawutdeveloper.trutho.ui.player.formatTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NoteDetailScreen(noteId: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    val noteFlow = remember(noteId) { db.noteDao().observeNote(noteId) }
    val note by noteFlow.collectAsState(initial = null)

    val currentSong by PlayerManager.currentSong.collectAsState()
    val isPlaying by PlayerManager.isPlaying.collectAsState()

    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    LaunchedEffect(Unit) {
        songs = withContext(Dispatchers.IO) {
            runCatching { SongScanner(context).scanSongs() }.getOrDefault(emptyList())
        }
    }

    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(editing) {
        if (editing) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    val current = note
    val song = songs.firstOrNull { it.id == current?.songId }
    val sameSongPlaying = song != null && currentSong?.id == song.id && isPlaying

    val startEdit: () -> Unit = {
        if (current != null) {
            draft = current.text
            editing = true
        }
    }

    val requestCancelEdit: () -> Unit = {
        if (current != null && draft.trim() != current.text.trim()) {
            showDiscardConfirm = true
        } else {
            editing = false
        }
    }

    val save: () -> Unit = {
        val text = draft.trim()
        if (text.isEmpty()) {
            Toast.makeText(context, "A note can't be empty", Toast.LENGTH_SHORT).show()
        } else {
            scope.launch { db.noteDao().updateText(noteId, text) }
            editing = false
        }
    }

    val playFromNote: () -> Unit = {
        if (song != null && current != null) {
            val moment = current.timestampMs
            if (currentSong?.id == song.id) {
                if (moment != null) PlayerManager.seekTo(moment)
                if (!isPlaying) PlayerManager.togglePlayPause()
            } else {
                PlayerManager.playSong(song, listOf(song), moment ?: 0L)
            }
        }
    }

    BackHandler(enabled = editing) { requestCancelEdit() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (editing) requestCancelEdit() else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                if (editing) "Edit note" else "Note",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (editing) {
                TextButton(onClick = requestCancelEdit) { Text("Cancel") }
                FilledTonalButton(onClick = save) { Text("Save") }
                Spacer(Modifier.width(8.dp))
            } else {
                IconButton(onClick = startEdit, enabled = current != null) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit note")
                }
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = current != null
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete note")
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            if (current != null) {
                // The song this note belongs to
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SongThumbnail(
                        song = song,
                        cornerRadius = 12.dp,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            song?.title ?: "Song not found",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            song?.artist ?: "This file is no longer on your phone",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (sameSongPlaying) PlayerManager.togglePlayPause() else playFromNote()
                        },
                        enabled = song != null,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            if (sameSongPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (sameSongPlaying) "Pause" else "Play"
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Time in the song and date of the note
                val moment = current.timestampMs
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (moment != null) {
                        TimePill(label = "At ${formatTime(moment)}")
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        formatNoteDate(current.createdAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (moment != null && sameSongPlaying && !editing) {
                    TextButton(onClick = { PlayerManager.seekTo(moment) }) {
                        Text("Jump to ${formatTime(moment)}")
                    }
                }

                Spacer(Modifier.height(20.dp))

                // The note itself
                if (editing) {
                    TextField(
                        value = draft,
                        onValueChange = { draft = it },
                        minLines = 6,
                        shape = RoundedCornerShape(16.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                } else {
                    SelectionContainer {
                        Text(
                            current.text,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this note?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    val target = note
                    if (target != null) {
                        scope.launch {
                            db.noteDao().deleteNote(target)
                            onBack()
                        }
                    }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits to this note have not been saved.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    editing = false
                }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("Keep editing") }
            }
        )
    }
}
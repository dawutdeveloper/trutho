package com.dawutdeveloper.trutho.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dawutdeveloper.trutho.data.local.AppDatabase
import com.dawutdeveloper.trutho.data.local.Note
import com.dawutdeveloper.trutho.data.media.SongScanner
import com.dawutdeveloper.trutho.domain.Song
import com.dawutdeveloper.trutho.ui.components.SongThumbnail
import com.dawutdeveloper.trutho.ui.player.formatTime
import java.text.DateFormat
import java.util.Date
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.dawutdeveloper.trutho.ui.components.FavoriteButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun formatNoteDate(millis: Long): String {
    val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    return format.format(Date(millis))
}

@Composable
fun TimePill(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun NotesScreen(onOpenNote: (Int) -> Unit, favoritesOnly: Boolean = false) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val db = remember { AppDatabase.getInstance(context) }

    val notesFlow = remember { db.noteDao().observeAll() }
    val allNotes by notesFlow.collectAsState(initial = emptyList())
    val notes = if (favoritesOnly) allNotes.filter { it.isFavorite } else allNotes
    val scope = rememberCoroutineScope()

    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val mediaRevision by com.dawutdeveloper.trutho.data.media.MediaChanges.revision.collectAsState()
    LaunchedEffect(mediaRevision) {
        songs = withContext(Dispatchers.IO) {
            runCatching { SongScanner(context).scanSongs() }.getOrDefault(emptyList())
        }
    }

    var query by rememberSaveable { mutableStateOf("") }

    val songById = songs.associateBy { it.id }

    val visible = remember(notes, songs, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            notes
        } else {
            val byId = songs.associateBy { it.id }
            notes.filter { note ->
                val song = byId[note.songId]
                note.text.contains(q, ignoreCase = true) ||
                        (song != null &&
                                (song.title.contains(q, ignoreCase = true) ||
                                        song.artist.contains(q, ignoreCase = true)))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            Text(if (favoritesOnly) "Favorite notes" else "Notes", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (notes.size == 1) "1 note" else "${notes.size} notes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val clearButton: (@Composable () -> Unit)? = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { query = "" }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        } else {
            null
        }

        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            placeholder = { Text("Search notes or songs") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = clearButton,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        if (visible.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (notes.isEmpty() && favoritesOnly) {
                        "No favorite notes yet. Tap the heart beside a note."
                    } else if (notes.isEmpty()) {
                        "No notes yet. Open a song and tap the notes icon to write one."
                    } else {
                        "No notes match your search"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)
            ) {
                items(visible, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        song = songById[note.songId],
                        onClick = { onOpenNote(note.id) },
                        onFavorite = { scope.launch { db.noteDao().toggleFavorite(note.id) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteCard(note: Note, song: Song?, onClick: () -> Unit, onFavorite: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SongThumbnail(
                song = song,
                cornerRadius = 8.dp,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song?.title ?: "Song not found",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (song != null) {
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            FavoriteButton(note.isFavorite, onFavorite)
            val ms = note.timestampMs
            if (ms != null) {
                Spacer(Modifier.width(8.dp))
                TimePill(label = formatTime(ms))
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            note.text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(10.dp))

        Text(
            formatNoteDate(note.createdAt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
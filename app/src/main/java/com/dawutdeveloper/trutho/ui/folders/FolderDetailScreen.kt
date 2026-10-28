package com.dawutdeveloper.trutho.ui.folders

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dawutdeveloper.trutho.data.local.AppDatabase
import com.dawutdeveloper.trutho.data.media.SongScanner
import com.dawutdeveloper.trutho.data.player.PlayerManager
import com.dawutdeveloper.trutho.domain.Song
import com.dawutdeveloper.trutho.ui.components.FolderPickerDialog
import com.dawutdeveloper.trutho.ui.components.SelectionAction
import com.dawutdeveloper.trutho.ui.components.SelectionBar
import com.dawutdeveloper.trutho.ui.components.SongRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class FolderSort(val label: String) {
    ADDED("Order added"),
    TITLE("Title"),
    ARTIST("Artist"),
    NEWEST("Newest file")
}

@Composable
fun FolderDetailScreen(folderId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    val folderFlow = remember(folderId) { db.folderDao().observeFolder(folderId) }
    val idsFlow = remember(folderId) { db.folderDao().observeSongIds(folderId) }
    val allFoldersFlow = remember { db.folderDao().observeFolders() }
    val folder by folderFlow.collectAsState(initial = null)
    val songIds by idsFlow.collectAsState(initial = emptyList())
    val allFolders by allFoldersFlow.collectAsState(initial = emptyList())

    val currentSong by PlayerManager.currentSong.collectAsState()
    val isPlaying by PlayerManager.isPlaying.collectAsState()

    var allSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val mediaRevision by com.dawutdeveloper.trutho.data.media.MediaChanges.revision.collectAsState()
    LaunchedEffect(mediaRevision) {
        allSongs = withContext(Dispatchers.IO) {
            runCatching { SongScanner(context).scanSongs() }.getOrDefault(emptyList())
        }
    }

    var sortName by rememberSaveable { mutableStateOf(FolderSort.ADDED.name) }
    val sort = FolderSort.valueOf(sortName)
    val listState = rememberLazyListState()
    var sortRequest by remember { mutableStateOf(0) }
    LaunchedEffect(sortRequest) { listState.scrollToItem(0) }

    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showMovePicker by remember { mutableStateOf(false) }
    var showAddPicker by remember { mutableStateOf(false) }

    val songById = allSongs.associateBy { it.id }
    val baseSongs = songIds.mapNotNull { songById[it] }
    val songs = when (sort) {
        FolderSort.ADDED -> baseSongs
        FolderSort.TITLE -> baseSongs.sortedBy { it.title.lowercase() }
        FolderSort.ARTIST -> baseSongs.sortedWith(
            compareBy<Song>({ it.artist.lowercase() }, { it.title.lowercase() })
        )
        FolderSort.NEWEST -> baseSongs.sortedByDescending { it.dateAdded }
    }

    val selectionMode = selectedIds.isNotEmpty()
    val selectedSongs = songs.filter { it.id in selectedIds }
    val otherFolders = allFolders.filter { it.id != folderId }

    val toggle: (Long) -> Unit = { id ->
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    BackHandler(enabled = selectionMode) { selectedIds = emptySet() }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectionMode) {
            SelectionBar(
                count = selectedIds.size,
                onClose = { selectedIds = emptySet() },
                onSelectAll = { selectedIds = songs.map { it.id }.toSet() },
                actions = listOf(
                    SelectionAction("Move") { showMovePicker = true },
                    SelectionAction("Add to folder") { showAddPicker = true },
                    SelectionAction("Remove", isDestructive = true) {
                        val ids = selectedSongs.map { it.id }
                        scope.launch { db.folderDao().removeSongs(folderId, ids) }
                        Toast.makeText(context, "Removed from folder", Toast.LENGTH_SHORT).show()
                        selectedIds = emptySet()
                    }
                )
            )
        } else {
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

            if (baseSongs.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FolderSort.entries.forEach { option ->
                        FilterChip(
                            selected = sort == option,
                            onClick = { sortName = option.name; sortRequest++ },
                            label = { Text(option.label) }
                        )
                    }
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
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(songs, key = { it.id }) { song ->
                    val removeButton: (@Composable () -> Unit)? = if (selectionMode) {
                        null
                    } else {
                        {
                            IconButton(onClick = {
                                scope.launch { db.folderDao().removeSong(folderId, song.id) }
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove from folder")
                            }
                        }
                    }

                    SongRow(
                        song = song,
                        isCurrent = currentSong?.id == song.id,
                        isPlaying = isPlaying,
                        selectionMode = selectionMode,
                        isSelected = song.id in selectedIds,
                        onClick = {
                            if (selectionMode) {
                                toggle(song.id)
                            } else {
                                PlayerManager.playSong(song, songs)
                            }
                        },
                        onLongClick = { toggle(song.id) },
                        trailing = removeButton
                    )

                }
            }
        }
    }

    if (showMovePicker) {
        FolderPickerDialog(
            title = "Move to folder",
            folders = otherFolders,
            onPick = { target ->
                val ids = selectedSongs.map { it.id }
                scope.launch { db.folderDao().moveSongs(folderId, target.id, ids) }
                Toast.makeText(context, "Moved to ${target.name}", Toast.LENGTH_SHORT).show()
                showMovePicker = false
                selectedIds = emptySet()
            },
            onDismiss = { showMovePicker = false }
        )
    }

    if (showAddPicker) {
        FolderPickerDialog(
            title = "Add to folder",
            folders = otherFolders,
            onPick = { target ->
                val ids = selectedSongs.map { it.id }
                scope.launch { db.folderDao().addSongs(target.id, ids) }
                Toast.makeText(context, "Added to ${target.name}", Toast.LENGTH_SHORT).show()
                showAddPicker = false
                selectedIds = emptySet()
            },
            onDismiss = { showAddPicker = false }
        )
    }
}
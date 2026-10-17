package com.dawutdeveloper.trutho.ui.library

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dawutdeveloper.trutho.data.local.AppDatabase
import com.dawutdeveloper.trutho.data.local.forgetSongs
import com.dawutdeveloper.trutho.data.local.setKaizenStatus
import com.dawutdeveloper.trutho.data.media.SongScanner
import com.dawutdeveloper.trutho.data.player.PlayerManager
import com.dawutdeveloper.trutho.domain.Song
import com.dawutdeveloper.trutho.ui.components.FolderPickerDialog
import com.dawutdeveloper.trutho.ui.components.KaizenStatusDialog
import com.dawutdeveloper.trutho.ui.components.SelectionAction
import com.dawutdeveloper.trutho.ui.components.SelectionBar
import com.dawutdeveloper.trutho.ui.components.SongRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SortOption(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    NEWEST("Newest")
}

@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    val foldersFlow = remember { db.folderDao().observeFolders() }
    val folders by foldersFlow.collectAsState(initial = emptyList())

    val currentSong by PlayerManager.currentSong.collectAsState()
    val isPlaying by PlayerManager.isPlaying.collectAsState()

    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    var query by rememberSaveable { mutableStateOf("") }
    var sortName by rememberSaveable { mutableStateOf(SortOption.TITLE.name) }
    val sort = SortOption.valueOf(sortName)
    val listState = rememberLazyListState()

    LaunchedEffect(sortName) {
        listState.scrollToItem(0)
    }

    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var addTargets by remember { mutableStateOf<List<Song>?>(null) }
    var showKaizenPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<List<Song>>(emptyList()) }

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, audioPermission
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    // Android shows its own confirmation when files are deleted
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val deleted = pendingDelete
        pendingDelete = emptyList()
        if (result.resultCode == Activity.RESULT_OK && deleted.isNotEmpty()) {
            scope.launch {
                val ids = deleted.map { it.id }
                withContext(Dispatchers.IO) { db.forgetSongs(ids) }
                val idSet = ids.toSet()
                songs = songs.filter { it.id !in idSet }
                PlayerManager.onSongsDeleted(deleted)
                selectedIds = emptySet()
                Toast.makeText(
                    context,
                    if (deleted.size == 1) "Song deleted" else "${deleted.size} songs deleted",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            loaded = false
            songs = withContext(Dispatchers.IO) {
                runCatching { SongScanner(context).scanSongs() }.getOrDefault(emptyList())
            }
            loaded = true
        }
    }

    val visible = remember(songs, query, sort) {
        val q = query.trim()
        val base = if (q.isEmpty()) {
            songs
        } else {
            songs.filter {
                it.title.contains(q, ignoreCase = true) ||
                        it.artist.contains(q, ignoreCase = true)
            }
        }
        when (sort) {
            SortOption.TITLE -> base.sortedBy { it.title.lowercase() }
            SortOption.ARTIST -> base.sortedWith(
                compareBy<Song>({ it.artist.lowercase() }, { it.title.lowercase() })
            )
            SortOption.NEWEST -> base.sortedByDescending { it.dateAdded }
        }
    }

    val selectionMode = selectedIds.isNotEmpty()
    val selectedSongs = visible.filter { it.id in selectedIds }

    val toggle: (Long) -> Unit = { id ->
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    BackHandler(enabled = selectionMode) { selectedIds = emptySet() }

    when {
        !hasPermission -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Permission needed to read your music files")
                    TextButton(onClick = { permissionLauncher.launch(audioPermission) }) {
                        Text("Grant permission")
                    }
                }
            }
        }

        !loaded -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading songs...")
            }
        }

        songs.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No songs found on this device")
            }
        }

        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                if (selectionMode) {
                    SelectionBar(
                        count = selectedIds.size,
                        onClose = { selectedIds = emptySet() },
                        onSelectAll = { selectedIds = visible.map { it.id }.toSet() },
                        actions = listOf(
                            SelectionAction("Add to folder") { addTargets = selectedSongs },
                            SelectionAction("Kaizen") { showKaizenPicker = true },
                            SelectionAction("Delete", isDestructive = true) {
                                showDeleteConfirm = true
                            }
                        )
                    )
                } else {
                    Column(
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)
                    ) {
                        Text("Library", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            if (songs.size == 1) "1 song" else "${songs.size} songs",
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
                        placeholder = { Text("Search songs or artists") },
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

                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SortOption.entries.forEach { option ->
                            FilterChip(
                                selected = sort == option,
                                onClick = { sortName = option.name },
                                label = { Text(option.label) }
                            )
                        }
                    }
                }

                if (visible.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No songs match your search",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp)
                    ) {
                        items(visible, key = { it.id }) { song ->
                            val addButton: (@Composable () -> Unit)? = if (selectionMode) {
                                null
                            } else {
                                {
                                    IconButton(onClick = { addTargets = listOf(song) }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.PlaylistAdd,
                                            contentDescription = "Add to folder",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
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
                                        PlayerManager.playSong(song, visible)
                                    }
                                },
                                onLongClick = { toggle(song.id) },
                                trailing = addButton
                            )
                        }
                    }
                }
            }
        }
    }

    addTargets?.let { targets ->
        FolderPickerDialog(
            title = if (targets.size == 1) "Add to folder" else "Add ${targets.size} songs to folder",
            folders = folders,
            onPick = { folder ->
                val ids = targets.map { it.id }
                scope.launch { db.folderDao().addSongs(folder.id, ids) }
                Toast.makeText(context, "Added to ${folder.name}", Toast.LENGTH_SHORT).show()
                addTargets = null
                selectedIds = emptySet()
            },
            onDismiss = { addTargets = null }
        )
    }

    if (showKaizenPicker) {
        KaizenStatusDialog(
            onPick = { status ->
                val ids = selectedSongs.map { it.id }
                scope.launch { db.setKaizenStatus(ids, status) }
                Toast.makeText(context, "Marked as ${status.label}", Toast.LENGTH_SHORT).show()
                showKaizenPicker = false
                selectedIds = emptySet()
            },
            onDismiss = { showKaizenPicker = false }
        )
    }

    if (showDeleteConfirm) {
        val count = selectedSongs.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (count == 1) "Delete this song?" else "Delete $count songs?") },
            text = {
                Text(
                    "The audio files will be permanently deleted from your phone, " +
                            "together with their notes and Kaizen status. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    val targets = selectedSongs
                    if (targets.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            pendingDelete = targets
                            val request = MediaStore.createDeleteRequest(
                                context.contentResolver,
                                targets.map { it.uri }
                            )
                            deleteLauncher.launch(
                                IntentSenderRequest.Builder(request.intentSender).build()
                            )
                        } catch (e: Exception) {
                            pendingDelete = emptyList()
                            Toast.makeText(
                                context,
                                "Could not start deleting",
                                Toast.LENGTH_SHORT
                            ).show()
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
}

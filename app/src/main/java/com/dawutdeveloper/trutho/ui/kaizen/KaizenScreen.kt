package com.dawutdeveloper.trutho.ui.kaizen

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dawutdeveloper.trutho.data.local.AppDatabase
import com.dawutdeveloper.trutho.data.local.KaizenStatus
import com.dawutdeveloper.trutho.data.local.TrackState
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

private fun stageHint(status: KaizenStatus): String = when (status) {
    KaizenStatus.MUST_LISTEN -> "Waiting in your queue"
    KaizenStatus.CURRENTLY_LISTENING -> "In progress right now"
    KaizenStatus.LISTENED -> "Finished listening"
    KaizenStatus.REPEAT -> "Listen again to absorb"
}

private fun stageEmptyText(status: KaizenStatus): String = when (status) {
    KaizenStatus.MUST_LISTEN ->
        "Your queue is empty. Mark songs as Must listen from the Library or the player."
    KaizenStatus.CURRENTLY_LISTENING ->
        "Nothing in progress. Songs you are listening to right now will show up here."
    KaizenStatus.LISTENED ->
        "Songs you finish will show up here."
    KaizenStatus.REPEAT ->
        "No songs to repeat. Set a song to Repeat in the player to track your repeats."
}

// How strong each stage looks in the progress bar and on the tab dots
private fun shadeFor(status: KaizenStatus): Float = when (status) {
    KaizenStatus.MUST_LISTEN -> 0.22f
    KaizenStatus.CURRENTLY_LISTENING -> 0.45f
    KaizenStatus.LISTENED -> 0.95f
    KaizenStatus.REPEAT -> 0.68f
}

@Composable
fun KaizenScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    val statesFlow = remember { db.trackStateDao().observeAll() }
    val states by statesFlow.collectAsState(initial = emptyList())
    val foldersFlow = remember { db.folderDao().observeFolders() }
    val folders by foldersFlow.collectAsState(initial = emptyList())

    val currentSong by PlayerManager.currentSong.collectAsState()
    val isPlaying by PlayerManager.isPlaying.collectAsState()

    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var scanFailed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loaded = false
        scanFailed = false

        val result = withContext(Dispatchers.IO) {
            runCatching {
                SongScanner(context).scanSongs()
            }
        }

        scanFailed = result.isFailure
        songs = result.getOrDefault(emptyList())
        loaded = true
    }


    var stageName by rememberSaveable { mutableStateOf(KaizenStatus.MUST_LISTEN.name) }
    val stage = KaizenStatus.valueOf(stageName)

    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showStatusPicker by remember { mutableStateOf(false) }
    var addTargets by remember { mutableStateOf<List<Song>?>(null) }
    var showAddSongsDialog by remember { mutableStateOf(false) }


    val songById = songs.associateBy { it.id }

    // Only count songs that still exist on the phone
    val tracked: List<TrackState> = if (loaded) {
        states.filter { songById.containsKey(it.songId) }
    } else {
        states
    }
    val countByStatus = tracked.groupingBy { it.status }.eachCount()
    val total = tracked.size
    val listenedCount = countByStatus[KaizenStatus.LISTENED] ?: 0

    val stageStates = tracked.filter { it.status == stage }
    val orderedStates = if (stage == KaizenStatus.MUST_LISTEN) {
        stageStates.sortedBy { it.updatedAt }
    } else {
        stageStates.sortedByDescending { it.updatedAt }
    }
    val stageItems: List<Pair<Song, TrackState>> = orderedStates.mapNotNull { state ->
        songById[state.songId]?.let { song -> song to state }
    }
    val stageSongs = stageItems.map { it.first }

    val selectionMode = selectedIds.isNotEmpty()
    val selectedSongs = stageSongs.filter { it.id in selectedIds }

    val toggle: (Long) -> Unit = { id ->
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    BackHandler(enabled = selectionMode) { selectedIds = emptySet() }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectionMode) {
            SelectionBar(
                count = selectedIds.size,
                onClose = { selectedIds = emptySet() },
                onSelectAll = { selectedIds = stageSongs.map { it.id }.toSet() },
                actions = listOf(
                    SelectionAction("Move to") { showStatusPicker = true },
                    SelectionAction("Add to folder") { addTargets = selectedSongs },
                    SelectionAction("Stop tracking", isDestructive = true) {
                        val ids = selectedSongs.map { it.id }
                        scope.launch {
                            ids.chunked(500).forEach { chunk ->
                                db.trackStateDao().deleteForSongs(chunk)
                            }
                        }
                        Toast.makeText(context, "Removed from Kaizen", Toast.LENGTH_SHORT).show()
                        selectedIds = emptySet()
                    }
                )
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (!selectionMode) {
                // Header
                item {
                    Column(
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)
                    ) {
                        Text("Kaizen", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            if (total == 1) "1 song on your path" else "$total songs on your path",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Progress overview
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
                    ) {
                        DistributionBar(counts = countByStatus, total = total)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (total == 0) {
                                "Mark songs as Must listen to begin."
                            } else {
                                "$listenedCount of $total listened"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Stage tabs
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KaizenStatus.entries.forEach { status ->
                            StageTab(
                                status = status,
                                count = countByStatus[status] ?: 0,
                                selected = stage == status,
                                onClick = { selectedIds = emptySet(); stageName = status.name },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Stage header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 12.dp, top = 24.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stage.label, style = MaterialTheme.typography.titleLarge)
                            Text(
                                stageHint(stage),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { showAddSongsDialog = true },
                            enabled = loaded && !scanFailed
                        ) {
                            Text("Add songs")
                        }
                        if (stageSongs.isNotEmpty()) {
                            FilledTonalButton(
                                onClick = { PlayerManager.playSong(stageSongs.first(), stageSongs) }
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Play all")
                            }
                        }
                    }
                }
            }

            if (stageItems.isEmpty()) {
                if (loaded) {
                    item {
                        Text(
                            if (scanFailed) "Unable to read songs. Check music permissions in Library."
                            else stageEmptyText(stage),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }
            } else {
                items(stageItems, key = { it.first.id }) { (song, state) ->
                    val progress: (@Composable () -> Unit)? =
                        if (!selectionMode && state.status == KaizenStatus.REPEAT) {
                            {
                                RepeatProgress(
                                    done = state.completedRepeats,
                                    target = state.targetRepeats
                                )
                            }
                        } else {
                            null
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
                                PlayerManager.playSong(song, stageSongs)
                            }
                        },
                        onLongClick = { toggle(song.id) },
                        trailing = progress
                    )
                }
            }
        }
    }

    if (showStatusPicker) {
        KaizenStatusDialog(
            onPick = { status ->
                val ids = selectedSongs.map { it.id }
                scope.launch { db.setKaizenStatus(ids, status) }
                Toast.makeText(context, "Moved to ${status.label}", Toast.LENGTH_SHORT).show()
                showStatusPicker = false
                selectedIds = emptySet()
            },
            onDismiss = { showStatusPicker = false }
        )
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

    if (showAddSongsDialog) {
        val alreadyAddedIds = states
            .asSequence()
            .filter { it.status == stage }
            .map { it.songId }
            .toSet()

        AddSongsToKaizenDialog(
            songs = songs,
            alreadyAddedIds = alreadyAddedIds,
            stage = stage,
            onConfirm = { ids ->
                scope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            db.setKaizenStatus(ids, stage)
                        }
                    }
                    if (result.isSuccess) {
                        showAddSongsDialog = false
                        Toast.makeText(
                            context,
                            "${ids.size} songs added to ${stage.label}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(context, "Could not add songs", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showAddSongsDialog = false }
        )
    }
}

@Composable
private fun AddSongsToKaizenDialog(
    songs: List<Song>,
    alreadyAddedIds: Set<Long>,
    stage: KaizenStatus,
    onConfirm: (List<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val available = remember(songs, alreadyAddedIds) {
        songs.filter { it.id !in alreadyAddedIds }
    }
    val filtered = remember(available, query) {
        available.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add songs to ${stage.label}") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search songs") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 350.dp)) {
                    items(filtered, key = { it.id }) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (song.id in selected) {
                                        selected - song.id
                                    } else {
                                        selected + song.id
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = song.id in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) {
                                        selected + song.id
                                    } else {
                                        selected - song.id
                                    }
                                }
                            )
                            Column {
                                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty(),
                onClick = { onConfirm(selected.toList()) }
            ) { Text("Add ${selected.size}") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// One segmented bar that shows how your songs are spread over the four stages
@Composable
private fun DistributionBar(counts: Map<KaizenStatus, Int>, total: Int) {
    if (total == 0) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            KaizenStatus.entries.forEach { status ->
                val count = counts[status] ?: 0
                if (count > 0) {
                    Box(
                        modifier = Modifier
                            .weight(count.toFloat())
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = shadeFor(status))
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun StageTab(
    status: KaizenStatus,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val mutedColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shade = shadeFor(status)
    val dotColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f + shade * 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = shade)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "$count",
            style = MaterialTheme.typography.headlineSmall,
            color = contentColor
        )
        Text(
            status.label,
            style = MaterialTheme.typography.labelSmall,
            color = mutedColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Small ring that fills as you finish repeats, for example 1/3
@Composable
private fun RepeatProgress(done: Int, target: Int) {
    val fraction = if (target > 0) {
        (done.toFloat() / target.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = Modifier.size(44.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text("$done/$target", style = MaterialTheme.typography.labelSmall)
    }
}

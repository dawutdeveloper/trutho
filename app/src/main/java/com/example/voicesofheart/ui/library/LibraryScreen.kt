package com.example.voicesofheart.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.content.ContextCompat
import com.example.voicesofheart.data.local.AppDatabase
import com.example.voicesofheart.data.local.FolderSong
import com.example.voicesofheart.data.media.SongScanner
import com.example.voicesofheart.data.player.PlayerManager
import com.example.voicesofheart.domain.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    val foldersFlow = remember { db.folderDao().observeFolders() }
    val folders by foldersFlow.collectAsState(initial = emptyList())

    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var songToAdd by remember { mutableStateOf<Song?>(null) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            launcher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            songs = withContext(Dispatchers.IO) { SongScanner(context).scanSongs() }
        }
    }

    if (!hasPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Permission needed to read your music files")
        }
        return
    }

    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No songs found on this device")
        }
        return
    }

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
                    IconButton(onClick = { songToAdd = song }) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "Add to folder"
                        )
                    }
                },
                modifier = Modifier.clickable { PlayerManager.playSong(song, songs) }
            )
            HorizontalDivider()
        }
    }

    songToAdd?.let { song ->
        AlertDialog(
            onDismissRequest = { songToAdd = null },
            title = { Text("Add to folder") },
            text = {
                if (folders.isEmpty()) {
                    Text("No folders yet. Create one in the Folders tab first.")
                } else {
                    LazyColumn {
                        items(folders, key = { it.id }) { folder ->
                            ListItem(
                                headlineContent = { Text(folder.name) },
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        db.folderDao().addSong(
                                            FolderSong(folderId = folder.id, songId = song.id)
                                        )
                                    }
                                    Toast.makeText(
                                        context,
                                        "Added to ${folder.name}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    songToAdd = null
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { songToAdd = null }) { Text("Close") }
            }
        )
    }
}
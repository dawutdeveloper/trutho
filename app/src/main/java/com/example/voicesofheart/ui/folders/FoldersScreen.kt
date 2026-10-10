package com.example.voicesofheart.ui.folders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.example.voicesofheart.data.local.AppDatabase
import com.example.voicesofheart.data.local.Folder
import kotlinx.coroutines.launch

@Composable
fun FoldersScreen(onOpenFolder: (Long) -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    val foldersFlow = remember { db.folderDao().observeFolders() }
    val countsFlow = remember { db.folderDao().observeCounts() }
    val folders by foldersFlow.collectAsState(initial = emptyList())
    val counts by countsFlow.collectAsState(initial = emptyList())
    val countMap = counts.associate { it.folderId to it.count }

    var showCreate by remember { mutableStateOf(false) }
    var folderToDelete by remember { mutableStateOf<Folder?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (folders.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No folders yet. Tap + to create one.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(folders, key = { it.id }) { folder ->
                    val count = countMap[folder.id] ?: 0
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                        headlineContent = {
                            Text(folder.name, style = MaterialTheme.typography.titleMedium)
                        },
                        supportingContent = {
                            Text(
                                if (count == 1) "1 song" else "$count songs",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { folderToDelete = folder }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete folder")
                            }
                        },
                        modifier = Modifier.clickable { onOpenFolder(folder.id) }
                    )
                    HorizontalDivider()
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreate = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "New folder")
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("Folder name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        scope.launch { db.folderDao().insertFolder(Folder(name = name.trim())) }
                        showCreate = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Cancel") }
            }
        )
    }

    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("Delete folder?") },
            text = { Text("\"${folder.name}\" will be deleted. Your songs stay on the phone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { db.folderDao().deleteFolder(folder.id) }
                    folderToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) { Text("Cancel") }
            }
        )
    }
}
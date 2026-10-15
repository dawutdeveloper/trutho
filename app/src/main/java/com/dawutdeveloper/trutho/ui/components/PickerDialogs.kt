package com.dawutdeveloper.trutho.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.dawutdeveloper.trutho.data.local.Folder
import com.dawutdeveloper.trutho.data.local.KaizenStatus

@Composable
fun FolderPickerDialog(
    title: String,
    folders: List<Folder>,
    onPick: (Folder) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (folders.isEmpty()) {
                Text("No folders available. Create one in the Folders tab first.")
            } else {
                LazyColumn {
                    items(folders, key = { it.id }) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            leadingContent = {
                                Icon(Icons.Filled.Folder, contentDescription = null)
                            },
                            // Transparent so the row blends into the dialog surface
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { onPick(folder) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun KaizenStatus.icon(): ImageVector = when (this) {
    KaizenStatus.MUST_LISTEN -> Icons.Filled.Bookmark
    KaizenStatus.CURRENTLY_LISTENING -> Icons.Filled.Headphones
    KaizenStatus.LISTENED -> Icons.Filled.CheckCircle
    KaizenStatus.REPEAT -> Icons.Filled.Repeat
}

@Composable
fun KaizenStatusDialog(
    onPick: (KaizenStatus) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Kaizen status") },
        text = {
            Column {
                KaizenStatus.entries.forEach { status ->
                    ListItem(
                        headlineContent = { Text(status.label) },
                        leadingContent = {
                            Icon(status.icon(), contentDescription = null)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onPick(status) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

package com.example.voicesofheart.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.voicesofheart.data.backup.BackupManager
import java.time.LocalDate
import kotlinx.coroutines.launch
import org.json.JSONException

private fun describeError(error: Throwable): String {
    return if (error is JSONException) {
        "The file is damaged or is not a valid backup."
    } else {
        error.message ?: "Something went wrong."
    }
}

private fun summaryText(summary: BackupManager.ImportSummary): String {
    return buildString {
        appendLine("Notes added: ${summary.notesAdded}")
        appendLine("Kaizen statuses restored: ${summary.statusesRestored}")
        appendLine("Folders added: ${summary.foldersAdded}")
        appendLine("Songs added to folders: ${summary.folderSongsAdded}")
        appendLine("Listens added: ${summary.listensAdded}")
        if (summary.songsNotFound > 0) {
            appendLine()
            append(
                "${summary.songsNotFound} songs from the backup were not found on this phone, " +
                        "so their notes and data were skipped."
            )
        }
    }.trim()
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            busy = true
            scope.launch {
                val result = BackupManager.exportTo(context, uri)
                busy = false
                dialog = result.fold(
                    onSuccess = { count ->
                        "Backup saved" to "$count items were saved to your file. " +
                                "Keep it somewhere safe, for example in a cloud drive or on your computer."
                    },
                    onFailure = { error -> "Backup failed" to describeError(error) }
                )
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            busy = true
            scope.launch {
                val result = BackupManager.importFrom(context, uri)
                busy = false
                dialog = result.fold(
                    onSuccess = { summary -> "Backup restored" to summaryText(summary) },
                    onFailure = { error -> "Restore failed" to describeError(error) }
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(20.dp)
            ) {
                Text("Backup and restore", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your notes, Kaizen progress, folders and listening history are stored " +
                            "only inside this app. Save them to a file to keep them safe. " +
                            "Your music files are not included.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "When you restore, songs are matched by title, artist and length. " +
                            "Restoring the same file twice never creates duplicates, and nothing " +
                            "you already have is deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        exportLauncher.launch("voicesofheart-backup-${LocalDate.now()}.json")
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Export backup")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Import backup")
                }

                if (busy) {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Working...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    dialog?.let { (title, message) ->
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { dialog = null }) { Text("OK") }
            }
        )
    }
}
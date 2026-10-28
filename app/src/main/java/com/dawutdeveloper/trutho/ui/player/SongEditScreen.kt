package com.dawutdeveloper.trutho.ui.player

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dawutdeveloper.trutho.data.media.SongMetadataEditor
import com.dawutdeveloper.trutho.data.media.SongScanner
import com.dawutdeveloper.trutho.data.player.PlayerManager
import com.dawutdeveloper.trutho.domain.Song
import kotlinx.coroutines.*

@Composable
fun SongEditScreen(songId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var song by remember { mutableStateOf<Song?>(null) }
    var title by rememberSaveable(songId) { mutableStateOf<String?>(null) }
    var artist by rememberSaveable(songId) { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingTitle by rememberSaveable { mutableStateOf("") }
    var pendingArtist by rememberSaveable { mutableStateOf("") }
    var discard by remember { mutableStateOf(false) }
    var resumeSave by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(songId) {
        song = withContext(Dispatchers.IO) { runCatching { SongScanner(context).scanSongs().firstOrNull { it.id == songId } }.getOrNull() }
        if (song == null) error = "This audio file is unavailable. Check audio access in Library."
        if (title == null) title = song?.title
        if (artist == null) artist = song?.artist
    }
    lateinit var saveAction: () -> Unit
    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) resumeSave = true
        else error = "File access was not granted. Your changes have not been saved."
    }
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) resumeSave = true else error = "Storage access is required to save changes to this file."
    }
    saveAction = {
        val target = song
        if (target != null && !saving) {
            saving = true
            error = null
            scope.launch {
                try {
                    val updated = SongMetadataEditor.save(context, target, pendingTitle, pendingArtist)
                    PlayerManager.onSongEdited(updated)
                    onBack()
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    try {
                        when {
                            failure is SecurityException && Build.VERSION.SDK_INT >= 30 -> {
                                val request = MediaStore.createWriteRequest(context.contentResolver, listOf(target.uri))
                                writePermission.launch(IntentSenderRequest.Builder(request.intentSender).build())
                            }
                            Build.VERSION.SDK_INT == 29 && failure is RecoverableSecurityException ->
                                writePermission.launch(IntentSenderRequest.Builder(failure.userAction.actionIntent.intentSender).build())
                            else -> error = failure.message ?: "Could not save the audio file."
                        }
                    } catch (permissionFailure: Exception) {
                        error = permissionFailure.message ?: "Cannot request access to this file."
                    }
                } finally { saving = false }
            }
        }
    }
    LaunchedEffect(resumeSave, song) {
        if (resumeSave && song != null && !saving) {
            resumeSave = false
            saveAction()
        }
    }
    val back: () -> Unit = {
        if (!saving) {
            if (song != null && (title != song?.title || artist != song?.artist)) discard = true else onBack()
        }
    }
    BackHandler { back() }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = back, enabled = !saving) { Text("Back") }
            Text("Edit song", style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(value = title.orEmpty(), onValueChange = { title = it }, label = { Text("Song title") },
            enabled = song != null && !saving, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = artist.orEmpty(), onValueChange = { artist = it }, label = { Text("Artist") },
            enabled = song != null && !saving, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Changes are saved to the audio file's title and artist tags.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = song != null && !saving && !title.isNullOrBlank(), onClick = {
            pendingTitle = title.orEmpty().trim()
            pendingArtist = artist.orEmpty().trim()
            if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                legacyPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else saveAction()
        }) { Text(if (saving) "Saving?" else "Save") }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard changes?") },
        confirmButton = { TextButton(onClick = onBack) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
}

package com.dawutdeveloper.trutho.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Filled.Home)
    object Library : Screen("library", "Library", Icons.Filled.LibraryMusic)
    object Kaizen : Screen("kaizen", "Kaizen", Icons.AutoMirrored.Filled.TrendingUp)
    object Folders : Screen("folders", "Folders", Icons.Filled.Folder)
    object Notes : Screen("notes", "Notes", Icons.Filled.EditNote)
    object Favorites : Screen("favorites", "Favorites", Icons.Filled.LibraryMusic)
    object SongEdit : Screen("song-edit/{songId}", "Edit song", Icons.Filled.EditNote) {
        fun createRoute(id: Long) = "song-edit/$id"
    }
    object Stats : Screen("stats", "Stats", Icons.Filled.BarChart)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)

    object Player : Screen("player/{songId}", "Player", Icons.Filled.PlayArrow) {
        fun createRoute(songId: Long) = "player/$songId"
    }

    object FolderDetail : Screen("folder/{folderId}", "Folder", Icons.Filled.Folder) {
        fun createRoute(folderId: Long) = "folder/$folderId"
    }

    object NoteDetail : Screen("note/{noteId}", "Note", Icons.Filled.EditNote) {
        fun createRoute(noteId: Int) = "note/$noteId"
    }
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Library,
    Screen.Kaizen,
    Screen.Folders,
    Screen.Notes
)
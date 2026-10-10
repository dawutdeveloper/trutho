package com.example.voicesofheart.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Filled.Home)
    object Library : Screen("library", "Library", Icons.Filled.LibraryMusic)
    object Kaizen : Screen("kaizen", "Kaizen", Icons.AutoMirrored.Filled.TrendingUp)
    object Folders : Screen("folders", "Folders", Icons.Filled.Folder)

    object Player : Screen("player/{songId}", "Player", Icons.Filled.PlayArrow) {
        fun createRoute(songId: Long) = "player/$songId"
    }

    object FolderDetail : Screen("folder/{folderId}", "Folder", Icons.Filled.Folder) {
        fun createRoute(folderId: Long) = "folder/$folderId"
    }
}

val bottomNavItems = listOf(Screen.Home, Screen.Library, Screen.Kaizen, Screen.Folders)
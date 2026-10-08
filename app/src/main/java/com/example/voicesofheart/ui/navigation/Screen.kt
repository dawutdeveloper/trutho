package com.example.voicesofheart.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.PlayArrow

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Filled.Home)
    object Library : Screen("library", "Library", Icons.Filled.LibraryMusic)
    object Kaizen : Screen("kaizen", "Kaizen", Icons.Filled.TrendingUp)
    object Folders : Screen("folders", "Folders", Icons.Filled.Folder)
    object Player : Screen("player/{songId}", "Player", Icons.Filled.PlayArrow) {
        fun createRoute(songId: Long) = "player/$songId"
    }
}

val bottomNavItems = listOf(Screen.Home, Screen.Library, Screen.Kaizen, Screen.Folders)
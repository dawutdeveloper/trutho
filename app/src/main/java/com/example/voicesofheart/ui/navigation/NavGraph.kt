package com.example.voicesofheart.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.voicesofheart.ui.folders.FolderDetailScreen
import com.example.voicesofheart.ui.folders.FoldersScreen
import com.example.voicesofheart.ui.home.HomeScreen
import com.example.voicesofheart.ui.kaizen.KaizenScreen
import com.example.voicesofheart.ui.library.LibraryScreen
import com.example.voicesofheart.ui.notes.NoteDetailScreen
import com.example.voicesofheart.ui.notes.NotesScreen
import com.example.voicesofheart.ui.player.PlayerScreen
import com.example.voicesofheart.ui.settings.SettingsScreen
import com.example.voicesofheart.ui.stats.StatsScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                onOpenPlayer = { id ->
                    navController.navigate(Screen.Player.createRoute(id)) {
                        launchSingleTop = true
                    }
                },
                onOpenStats = {
                    navController.navigate(Screen.Stats.route) {
                        launchSingleTop = true
                    }
                },
                onOpenSettings = {
                    navController.navigate(Screen.Settings.route) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Screen.Library.route) { LibraryScreen() }
        composable(Screen.Kaizen.route) { KaizenScreen() }

        composable(Screen.Folders.route) {
            FoldersScreen(onOpenFolder = { id ->
                navController.navigate(Screen.FolderDetail.createRoute(id))
            })
        }

        composable(Screen.Notes.route) {
            NotesScreen(onOpenNote = { id ->
                navController.navigate(Screen.NoteDetail.createRoute(id))
            })
        }

        composable(Screen.Stats.route) {
            StatsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.FolderDetail.route,
            arguments = listOf(navArgument("folderId") { type = NavType.LongType })
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getLong("folderId") ?: -1L
            FolderDetailScreen(folderId = folderId, onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.NoteDetail.route,
            arguments = listOf(navArgument("noteId") { type = NavType.IntType })
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getInt("noteId") ?: -1
            NoteDetailScreen(noteId = noteId, onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Player.route,
            arguments = listOf(navArgument("songId") { type = NavType.LongType })
        ) { backStackEntry ->
            val songId = backStackEntry.arguments?.getLong("songId") ?: -1L
            PlayerScreen(
                songId = songId,
                onBack = { navController.popBackStack() },
                onOpenNote = { noteId ->
                    navController.navigate(Screen.NoteDetail.createRoute(noteId))
                }
            )
        }
    }
}
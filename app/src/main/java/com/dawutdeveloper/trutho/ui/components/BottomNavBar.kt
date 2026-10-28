package com.dawutdeveloper.trutho.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.dawutdeveloper.trutho.ui.navigation.bottomNavItems
import com.dawutdeveloper.trutho.ui.navigation.Screen

@Composable
fun BottomNavBar(navController: NavController) {
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        bottomNavItems.forEach { screen ->
            val insideFolder = currentDestination?.route == Screen.FolderDetail.route
            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true ||
                (screen == Screen.Folders && insideFolder)

            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (screen == Screen.Folders && insideFolder) {
                        navController.popBackStack(Screen.Folders.route, false)
                    } else navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                // The label below already names the tab, so the icon stays silent for TalkBack
                icon = { Icon(screen.icon, contentDescription = null) },
                label = { Text(screen.label) }
            )
        }
    }
}
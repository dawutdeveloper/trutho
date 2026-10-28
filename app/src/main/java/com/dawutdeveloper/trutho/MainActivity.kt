package com.dawutdeveloper.trutho

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dawutdeveloper.trutho.data.player.PlayerManager
import com.dawutdeveloper.trutho.ui.components.BottomNavBar
import com.dawutdeveloper.trutho.ui.components.MiniPlayer
import com.dawutdeveloper.trutho.ui.navigation.NavGraph
import com.dawutdeveloper.trutho.ui.navigation.Screen
import com.dawutdeveloper.trutho.ui.theme.TruthoTheme

class MainActivity : ComponentActivity() {
    private val audioObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            com.dawutdeveloper.trutho.data.media.MediaChanges.notifyChanged()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        contentResolver.registerContentObserver(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, audioObserver)
        PlayerManager.init(applicationContext)
        setContent {
            TruthoApp()
        }
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(audioObserver)
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        com.dawutdeveloper.trutho.data.media.MediaChanges.notifyChanged()
    }

    override fun onStop() {
        super.onStop()
        PlayerManager.saveState()
    }
}

@Composable
fun TruthoApp() {
    TruthoTheme {
        val navController = rememberNavController()
        val currentSongState by PlayerManager.currentSong.collectAsState()

        // The player and the note screen are full screen: no mini player, no tabs
        val backStackEntry by navController.currentBackStackEntryAsState()
        val route = backStackEntry?.destination?.route
        val showBars = route != Screen.Player.route && route != Screen.NoteDetail.route && route != Screen.SongEdit.route

        Scaffold(
            bottomBar = {
                if (showBars) {
                    Column {
                        MiniPlayer(onClick = {
                            currentSongState?.let { song ->
                                navController.navigate(Screen.Player.createRoute(song.id)) {
                                    launchSingleTop = true
                                }
                            }
                        })
                        BottomNavBar(navController)
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                NavGraph(navController = navController)
            }
        }
    }
}
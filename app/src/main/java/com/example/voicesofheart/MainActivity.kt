package com.example.voicesofheart

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.navigation.compose.rememberNavController
import com.example.voicesofheart.data.player.PlayerManager
import com.example.voicesofheart.ui.components.BottomNavBar
import com.example.voicesofheart.ui.components.MiniPlayer
import com.example.voicesofheart.ui.navigation.NavGraph
import com.example.voicesofheart.ui.navigation.Screen
import com.example.voicesofheart.ui.theme.VoicesofheartTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PlayerManager.init(applicationContext)
        setContent {
            VoicesOfHeartApp()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        PlayerManager.release()
    }
}

@Composable
fun VoicesOfHeartApp() {
    VoicesofheartTheme {
        val navController = rememberNavController()
        val currentSongState by PlayerManager.currentSong.collectAsState()

        Scaffold(
            bottomBar = {
                Column {
                    MiniPlayer(onClick = {
                        currentSongState?.let { song ->
                            navController.navigate(Screen.Player.createRoute(song.id))
                        }
                    })
                    BottomNavBar(navController)
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                NavGraph(navController = navController)
            }
        }
    }
}
package com.example.voicesofheart.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.voicesofheart.data.local.AppDatabase
import com.example.voicesofheart.data.media.SongScanner
import com.example.voicesofheart.data.player.PlayerManager
import com.example.voicesofheart.domain.Song
import com.example.voicesofheart.ui.components.SongRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class DayBar(val label: String, val count: Int)

private fun currentStreak(days: Set<LocalDate>, today: LocalDate): Int {
    // If nothing was listened to today yet, yesterday's streak is still alive
    var cursor = if (days.contains(today)) today else today.minusDays(1)
    var count = 0
    while (days.contains(cursor)) {
        count++
        cursor = cursor.minusDays(1)
    }
    return count
}

private fun bestStreak(days: Set<LocalDate>): Int {
    var best = 0
    var run = 0
    var previous: LocalDate? = null
    for (day in days.sorted()) {
        val prev = previous
        run = if (prev != null && day == prev.plusDays(1)) run + 1 else 1
        if (run > best) best = run
        previous = day
    }
    return best
}

@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }

    val stampsFlow = remember { db.listenLogDao().observeAllTimestamps() }
    val topFlow = remember { db.listenLogDao().observeTopSongs() }
    val stamps by stampsFlow.collectAsState(initial = emptyList())
    val topCounts by topFlow.collectAsState(initial = emptyList())

    val currentSong by PlayerManager.currentSong.collectAsState()
    val isPlaying by PlayerManager.isPlaying.collectAsState()

    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    LaunchedEffect(Unit) {
        songs = withContext(Dispatchers.IO) {
            runCatching { SongScanner(context).scanSongs() }.getOrDefault(emptyList())
        }
    }

    val today = LocalDate.now()

    val perDay: Map<LocalDate, Int> = remember(stamps) {
        stamps
            .map { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
            .groupingBy { it }
            .eachCount()
    }

    val streak = currentStreak(perDay.keys, today)
    val best = remember(perDay) { bestStreak(perDay.keys) }

    val week = (6 downTo 0).map { offset ->
        val date = today.minusDays(offset.toLong())
        val label = if (offset == 0) {
            "Today"
        } else {
            date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
        DayBar(label, perDay[date] ?: 0)
    }
    val weekTotal = week.sumOf { it.count }
    val maxCount = (week.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)

    val songById = songs.associateBy { it.id }
    val topSongs = topCounts.mapNotNull { row ->
        songById[row.songId]?.let { song -> song to row.count }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Stats", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatBlock("$streak", "Day streak", Modifier.weight(1f))
                    StatBlock("$best", "Best streak", Modifier.weight(1f))
                    StatBlock("${stamps.size}", "All-time listens", Modifier.weight(1f))
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                    Text("Last 7 days", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (weekTotal == 1) "1 listen this week" else "$weekTotal listens this week",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    WeekChart(week = week, maxCount = maxCount)
                }
            }

            item {
                Text(
                    "Most listened",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp
                    )
                )
            }

            if (topSongs.isEmpty()) {
                item {
                    Text(
                        "Finish a song and it will show up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else {
                items(topSongs, key = { it.first.id }) { (song, count) ->
                    SongRow(
                        song = song,
                        isCurrent = currentSong?.id == song.id,
                        isPlaying = isPlaying,
                        extraInfo = if (count == 1) "1 listen" else "$count listens",
                        onClick = {
                            PlayerManager.playSong(song, topSongs.map { it.first })
                        }
                    )

                }
            }
        }
    }
}

@Composable
private fun StatBlock(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.tertiary
        )
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun WeekChart(week: List<DayBar>, maxCount: Int) {
    val chartHeight = 120.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        week.forEach { bar ->
            val fraction = bar.count.toFloat() / maxCount.toFloat()
            val barColor = if (bar.count == 0) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.tertiary
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("${bar.count}", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(maxOf(4.dp, chartHeight * fraction))
                        .background(barColor, RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}
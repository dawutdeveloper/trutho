package com.example.voicesofheart.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.voicesofheart.domain.Song

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    extraInfo: String? = null,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val rowShape = MaterialTheme.shapes.large

    // Selected rows use the secondary container, the playing row the primary
    // container. Text colors are the matching "on" colors so contrast stays
    // correct with any palette, including Material You dynamic colors.
    val containerColor = when {
        isSelected -> colors.secondaryContainer
        isCurrent -> colors.primaryContainer
        else -> Color.Transparent
    }
    val titleColor = when {
        isSelected -> colors.onSecondaryContainer
        isCurrent -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    val artistColor = when {
        isSelected -> colors.onSecondaryContainer
        isCurrent -> colors.onPrimaryContainer
        else -> colors.onSurfaceVariant
    }

    // Leading slot: checkbox in selection mode, otherwise a rounded tile that
    // shows the playback state of the current song or a music note.
    val leading: @Composable () -> Unit = {
        if (selectionMode) {
            Checkbox(checked = isSelected, onCheckedChange = null)
        } else {
            val tileColor = if (isCurrent) colors.primary else colors.surfaceContainerHighest
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(tileColor),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrent) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.Pause,
                        contentDescription = if (isPlaying) "Playing" else "Paused",
                        tint = colors.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant
                    )
                }
            }
        }
    }

    // The clip comes before the click handler so the ripple follows the
    // rounded corners.
    val shapedModifier = modifier
        .padding(horizontal = 8.dp, vertical = 2.dp)
        .clip(rowShape)

    val clickModifier = if (onLongClick != null) {
        shapedModifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        shapedModifier.clickable(onClick = onClick)
    }

    ListItem(
        headlineContent = {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                if (extraInfo != null) "${song.artist} · $extraInfo" else song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = artistColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = leading,
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = containerColor),
        modifier = clickModifier
    )
}

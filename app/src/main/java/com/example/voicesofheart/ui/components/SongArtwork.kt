package com.example.voicesofheart.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.voicesofheart.data.media.ArtworkLoader
import com.example.voicesofheart.domain.Song
import kotlin.random.Random

@Composable
fun SongArtwork(
    song: Song?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp
) {
    val context = LocalContext.current

    val bitmap by produceState<Bitmap?>(
        initialValue = song?.let { ArtworkLoader.cached(it.id) },
        song?.id
    ) {
        val cached = song?.let { ArtworkLoader.cached(it.id) }
        value = cached
        if (cached == null && song != null) {
            value = ArtworkLoader.load(context, song)
        }
    }

    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    ) {
        Crossfade(targetState = bitmap, label = "artwork") { bmp ->
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                GeneratedCover(seed = song?.id ?: 0L, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private class CoverSpec(
    val cx: Float,
    val cy: Float,
    val rings: Int,
    val gap: Float,
    val lightFirst: Boolean,
    val tone: Int
)

@Composable
fun GeneratedCover(seed: Long, modifier: Modifier = Modifier) {
    val spec = remember(seed) {
        val random = Random(seed)
        CoverSpec(
            cx = 0.3f + random.nextFloat() * 0.4f,
            cy = 0.3f + random.nextFloat() * 0.4f,
            rings = 8 + random.nextInt(7),
            gap = 0.055f + random.nextFloat() * 0.03f,
            lightFirst = random.nextBoolean(),
            tone = 0x26 + random.nextInt(0x1A)
        )
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val m = minOf(w, h)

        val light = Color(spec.tone, spec.tone, spec.tone)
        val dark = Color(0xFF0A0A0A)

        drawRect(
            brush = Brush.linearGradient(
                colors = if (spec.lightFirst) listOf(light, dark) else listOf(dark, light),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )

        val center = Offset(w * spec.cx, h * spec.cy)
        for (i in 1..spec.rings) {
            val alpha = (0.30f - i * 0.018f).coerceAtLeast(0.05f)
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = m * spec.gap * i,
                center = center,
                style = Stroke(width = m * 0.004f)
            )
        }
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = m * 0.010f,
            center = center
        )
    }
}
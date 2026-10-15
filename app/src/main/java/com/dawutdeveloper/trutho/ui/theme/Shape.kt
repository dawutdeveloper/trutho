package com.dawutdeveloper.trutho.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Material 3 corner scale. Components read these through MaterialTheme.shapes:
// small = chips, medium = artwork tiles, large = list rows and cards,
// extraLarge = mini player, selection bar and dialogs.
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

package com.dawutdeveloper.trutho.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PureWhite,
    onPrimary = Ink,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = PureWhite,
    inversePrimary = Ink,
    secondary = DarkSecondary,
    onSecondary = Ink,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = Ink,
    tertiary = DarkTertiary,
    onTertiary = Ink,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnSurface,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkBackground,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = PureWhite,
    inverseSurface = DarkOnSurface,
    inverseOnSurface = Ink,
    error = DarkError,
    onError = Ink,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    surfaceContainerLowest = DarkSurfaceLowest,
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHighest
)

private val LightColorScheme = lightColorScheme(
    primary = Ink,
    onPrimary = PureWhite,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = Ink,
    inversePrimary = PureWhite,
    secondary = LightSecondary,
    onSecondary = PureWhite,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = PureWhite,
    tertiary = LightTertiary,
    onTertiary = PureWhite,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = Ink,
    background = LightBackground,
    onBackground = Ink,
    surface = LightBackground,
    onSurface = Ink,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = Ink,
    inverseSurface = Ink,
    inverseOnSurface = PureWhite,
    error = LightError,
    onError = PureWhite,
    errorContainer = LightErrorContainer,
    onErrorContainer = Ink,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    surfaceContainerLowest = LightSurfaceLowest,
    surfaceContainerLow = LightSurfaceLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = LightSurfaceHighest
)

@Composable
fun TruthoTheme(
    // The app is dark by design. A Settings toggle for the light theme can come later.
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
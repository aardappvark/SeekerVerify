package com.seekerverify.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = SeekerBlue,
    onPrimary = Color.White,
    primaryContainer = SeekerDarkBlue,
    onPrimaryContainer = SeekerLightBlue,
    secondary = SolanaGreen,
    onSecondary = SpaceBlack,
    secondaryContainer = Color(0xFF0A3D2A),
    onSecondaryContainer = SolanaGreen,
    tertiary = SolanaPurple,
    onTertiary = Color.White,
    background = SpaceBlack,
    onBackground = SeekerWhite,
    surface = NightBlue,
    onSurface = SeekerWhite,
    surfaceVariant = CardBackground,
    onSurfaceVariant = SubtleText,
    error = SeekerRed,
    onError = Color.White,
    outline = CardBorder
)

private val LightColorScheme = lightColorScheme(
    primary = SeekerBlue,
    onPrimary = Color.White,
    primaryContainer = SeekerLightBlue,
    onPrimaryContainer = SeekerDarkBlue,
    secondary = Color(0xFF00C853),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB9F6CA),
    onSecondaryContainer = Color(0xFF003D1A),
    tertiary = SolanaPurple,
    onTertiary = Color.White,
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF1A1A2E),
    surface = Color.White,
    onSurface = Color(0xFF1A1A2E),
    surfaceVariant = Color(0xFFE8ECF0),
    onSurfaceVariant = Color(0xFF4A5568),
    error = SeekerRed,
    onError = Color.White,
    outline = Color(0xFFCBD5E0)
)

@Composable
fun SeekerVerifyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

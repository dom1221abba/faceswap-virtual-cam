package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF97F0FF),
    secondary = CyberPurple,
    onSecondary = Color(0xFF2E1568),
    secondaryContainer = Color(0xFF452E80),
    onSecondaryContainer = Color(0xFFEADBFF),
    tertiary = VirtualActiveGreen,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkSurfaceBorder
)

private val LightColorScheme = darkColorScheme(
    primary = CyberCyan,
    background = DarkBackground,
    surface = DarkSurface,
    onSurface = TextPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to sleek studio dark theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

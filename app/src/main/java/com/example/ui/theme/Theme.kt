package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarvisColorScheme = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = Color(0xFF041320),
    primaryContainer = JarvisCyanDark,
    onPrimaryContainer = Color(0xFFE0F7FA),
    secondary = JarvisBlue,
    onSecondary = Color.White,
    secondaryContainer = JarvisNavyCard,
    onSecondaryContainer = JarvisCyan,
    tertiary = JarvisGlowSpeaking,
    background = JarvisNavyDark,
    onBackground = TextPrimaryDark,
    surface = JarvisNavySurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = JarvisNavyCard,
    onSurfaceVariant = TextSecondaryDark,
    outline = JarvisBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = Typography,
        content = content
    )
}

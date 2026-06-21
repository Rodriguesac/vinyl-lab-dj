package com.rodriguesacai.vinyllab.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VinylColors = darkColorScheme(
    primary = Color(0xFFF6DD4A),
    onPrimary = Color(0xFF181818),
    secondary = Color(0xFF9FC7FF),
    background = Color(0xFF090A16),
    surface = Color(0xFF13152A),
    surfaceVariant = Color(0xFF20233C),
    onBackground = Color(0xFFF4F1FF),
    onSurface = Color(0xFFF4F1FF),
    outline = Color(0xFF8B91AA),
    error = Color(0xFFFFB4AB)
)

@Composable
fun VinylLabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VinylColors,
        content = content
    )
}

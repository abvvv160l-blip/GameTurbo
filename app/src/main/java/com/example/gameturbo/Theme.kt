package com.example.gameturbo

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GameColors = darkColorScheme(
    primary = Color(0xFFFF7A1A),
    onPrimary = Color(0xFF1A0E00),
    secondary = Color(0xFFFFB26B),
    onSecondary = Color(0xFF1A0E00),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFECEEF3),
    surface = Color(0xFF171A21),
    onSurface = Color(0xFFECEEF3),
    surfaceVariant = Color(0xFF222733),
    onSurfaceVariant = Color(0xFFA9B0C0),
    error = Color(0xFFFF5C5C),
    onError = Color(0xFF2B0000),
    errorContainer = Color(0xFF4A1C1C),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun GameTurboTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GameColors, content = content)
}

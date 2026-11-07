package com.voidlauncher.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Void Launcher is dark-only by design.
private val VoidColorScheme = darkColorScheme(
    primary = VoidPurple,
    onPrimary = Color.White,
    secondary = VoidBlue,
    onSecondary = Color.White,
    background = VoidBackground,
    onBackground = VoidOnBackground,
    surface = VoidSurface,
    onSurface = VoidOnBackground,
    surfaceVariant = VoidSurfaceHigh,
    onSurfaceVariant = VoidMuted,
)

@Composable
fun VoidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VoidColorScheme,
        content = content,
    )
}

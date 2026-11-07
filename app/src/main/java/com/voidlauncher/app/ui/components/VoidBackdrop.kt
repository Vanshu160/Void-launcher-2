package com.voidlauncher.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.voidlauncher.app.ui.theme.VoidBackground
import com.voidlauncher.app.ui.theme.VoidBlue
import com.voidlauncher.app.ui.theme.VoidPurple

/**
 * Dark backdrop with two slowly drifting glows and faint floating particles.
 * Set animated = false to keep it still and save battery.
 */
@Composable
fun VoidBackdrop(animated: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    val transition = rememberInfiniteTransition(label = "void-backdrop")
    val animatedPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "void-phase",
    )
    val phase = if (animated) animatedPhase else 0.5f

    Box(modifier = Modifier.fillMaxSize().background(VoidBackground)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(VoidPurple.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(w * (0.15f + 0.2f * phase), h * 0.25f),
                    radius = w * 0.7f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(VoidBlue.copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(w * (0.9f - 0.2f * phase), h * 0.85f),
                    radius = w * 0.6f,
                ),
            )

            for (i in 0 until 40) {
                val seedX = (i * 0.618034f) % 1f
                val seedY = (i * 0.381966f) % 1f
                val y = ((seedY + phase * 0.25f * (1 + i % 3)) % 1f) * h
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f + 0.1f * ((i % 4) / 4f)),
                    radius = (1.5f + (i % 3)).dp.toPx(),
                    center = Offset(seedX * w, y),
                )
            }
        }
        content()
    }
}

package com.voidlauncher.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.voidlauncher.app.data.launch.DEFAULT_BACKGROUND_ARGB
import com.voidlauncher.app.data.launch.DEFAULT_STROKE_ARGB
import com.voidlauncher.app.data.launch.GameInput
import com.voidlauncher.app.data.launch.TouchControls
import com.voidlauncher.app.data.launch.TouchGeometry
import kotlin.math.roundToInt

/**
 * Draws the on-screen buttons over the game surface, from the layout that is saved for this screen shape.
 * Input is not handled here: [com.voidlauncher.app.data.launch.TouchRouter] routes the touches, using the same
 * positions and sizes, so a button is where it looks.
 *
 * The overlay shows the buttons for the current context: the in-world buttons while Minecraft holds the cursor, and
 * the menu buttons otherwise. A held joystick draws its base where the finger landed in absolute mode, and its knob
 * follows the finger.
 */
@Composable
fun TouchControlsOverlay() {
    val settings by TouchControls.settings.collectAsState()
    if (!settings.enabled) return
    val inWorld by GameInput.inWorld.collectAsState()
    val layout by TouchControls.layout.collectAsState()
    val pressed by TouchControls.pressed.collectAsState()
    val joysticks by TouchControls.joysticks.collectAsState()
    var size by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { measured ->
                size = measured
                TouchControls.setScreenSize(measured.width, measured.height)
            },
    ) {
        if (size.width > 0 && size.height > 0) {
            val visible = layout.buttons.filter { if (inWorld) it.inWorld else it.inMenu }
            for (button in visible) {
                val (centreX, centreY) = TouchGeometry.centrePx(button, size.width, size.height)
                val radius = TouchGeometry.radiusPx(button, settings.scale, size.width, size.height)
                val opacity = settings.opacity * button.opacity
                if (button.isJoystick) {
                    val state = joysticks[button.id]
                    val baseX = state?.centreX ?: centreX
                    val baseY = state?.centreY ?: centreY
                    PlacedGlyph(
                        left = baseX - radius,
                        top = baseY - radius,
                        diameterPx = radius * 2f,
                        label = "",
                        opacity = opacity,
                        highlighted = state != null,
                        background = button.background,
                        stroke = button.stroke,
                        cornerRadius = 1f,
                    )
                    val knobRadius = radius * 0.5f
                    val knobX = state?.knobX ?: baseX
                    val knobY = state?.knobY ?: baseY
                    PlacedGlyph(
                        left = knobX - knobRadius,
                        top = knobY - knobRadius,
                        diameterPx = knobRadius * 2f,
                        label = "",
                        opacity = opacity,
                        highlighted = state != null,
                        background = button.background,
                        stroke = button.stroke,
                        cornerRadius = 1f,
                    )
                } else {
                    PlacedGlyph(
                        left = centreX - radius,
                        top = centreY - radius,
                        diameterPx = radius * 2f,
                        label = button.name,
                        opacity = opacity,
                        highlighted = button.id in pressed,
                        background = button.background,
                        stroke = button.stroke,
                        cornerRadius = button.cornerRadius,
                    )
                }
            }
        }
    }
}

/**
 * A button's glyph at its size. Used by the overlay and by the layout editor, so both draw a button the same way.
 * [background] and [stroke] are ARGB colours. [cornerRadius] runs from 0 (square) to 1 (round). A highlighted button
 * is filled with the accent colour while it is held or switched on.
 */
@Composable
internal fun TouchControlGlyph(
    label: String,
    diameter: Dp,
    opacity: Float,
    highlighted: Boolean,
    fillArgb: Long = DEFAULT_BACKGROUND_ARGB,
    stroke: Long = DEFAULT_STROKE_ARGB,
    cornerRadius: Float = 1f,
) {
    val colors = MaterialTheme.colorScheme
    val alpha = opacity.coerceIn(0f, 1f)
    val shape = if (cornerRadius >= 0.99f) {
        CircleShape
    } else {
        RoundedCornerShape(percent = (cornerRadius.coerceIn(0f, 1f) * 50f).roundToInt())
    }
    val fillColor = Color(fillArgb)
    val fill = if (highlighted) colors.primary.copy(alpha = 0.6f * alpha) else fillColor.copy(alpha = fillColor.alpha * alpha)
    val strokeColor = Color(stroke)
    val edge = strokeColor.copy(alpha = strokeColor.alpha * alpha)
    Box(
        modifier = Modifier
            .size(diameter)
            .clip(shape)
            .background(fill)
            .border(2.dp, edge, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurface.copy(alpha = alpha),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlacedGlyph(
    left: Float,
    top: Float,
    diameterPx: Float,
    label: String,
    opacity: Float,
    highlighted: Boolean,
    background: Long,
    stroke: Long,
    cornerRadius: Float,
) {
    val density = LocalDensity.current
    Box(modifier = Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }) {
        TouchControlGlyph(
            label = label,
            diameter = with(density) { diameterPx.toDp() },
            opacity = opacity,
            highlighted = highlighted,
            fillArgb = background,
            stroke = stroke,
            cornerRadius = cornerRadius,
        )
    }
}

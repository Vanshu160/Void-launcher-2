package com.voidlauncher.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.voidlauncher.app.data.launch.ControlAction
import com.voidlauncher.app.data.launch.GameInput
import com.voidlauncher.app.data.launch.TouchControl
import com.voidlauncher.app.data.launch.TouchControls
import com.voidlauncher.app.data.launch.TouchGeometry
import kotlin.math.roundToInt

/**
 * Draws the on-screen controls over the game surface. Input is not handled here: [com.voidlauncher.app.data.launch.TouchRouter]
 * routes the touches, using the same positions and sizes, so a control is where it looks.
 *
 * The movement stick and the in-world buttons show only while Minecraft holds the cursor. Menus show Esc only.
 * The knob follows the finger while the stick is held.
 */
@Composable
fun TouchControlsOverlay() {
    val settings by TouchControls.settings.collectAsState()
    if (!settings.enabled) return
    val inWorld by GameInput.inWorld.collectAsState()
    val layout by TouchControls.layout.collectAsState()
    val pressed by TouchControls.pressed.collectAsState()
    val knob by TouchControls.knob.collectAsState()
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
            for (control in TouchControls.visibleControls(inWorld)) {
                val (fx, fy) = layout[control] ?: continue
                val radius = TouchGeometry.radiusPx(control, settings.scale, size.width, size.height)
                val centerX = fx * size.width
                val centerY = fy * size.height
                val isJoystick = control.action == ControlAction.Joystick
                PlacedGlyph(
                    left = centerX - radius,
                    top = centerY - radius,
                    diameterPx = radius * 2f,
                    label = if (isJoystick) "" else control.label,
                    opacity = settings.opacity,
                    highlighted = control in pressed,
                )
                if (isJoystick) {
                    val knobRadius = radius * 0.5f
                    val (knobX, knobY) = knob ?: (centerX to centerY)
                    PlacedGlyph(
                        left = knobX - knobRadius,
                        top = knobY - knobRadius,
                        diameterPx = knobRadius * 2f,
                        label = "",
                        opacity = settings.opacity,
                        highlighted = knob != null,
                    )
                }
            }
        }
    }
}

/** A control's round glyph at a pixel position. Used by the overlay and by the layout editor. */
@Composable
internal fun TouchControlGlyph(label: String, diameter: Dp, opacity: Float, highlighted: Boolean) {
    val colors = MaterialTheme.colorScheme
    val tint = if (highlighted) colors.primary else colors.onSurface
    Box(
        modifier = Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(tint.copy(alpha = opacity * (if (highlighted) 0.5f else 0.2f)))
            .border(2.dp, tint.copy(alpha = opacity), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurface.copy(alpha = opacity),
                textAlign = TextAlign.Center,
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
) {
    val density = LocalDensity.current
    Box(modifier = Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }) {
        TouchControlGlyph(
            label = label,
            diameter = with(density) { diameterPx.toDp() },
            opacity = opacity,
            highlighted = highlighted,
        )
    }
}

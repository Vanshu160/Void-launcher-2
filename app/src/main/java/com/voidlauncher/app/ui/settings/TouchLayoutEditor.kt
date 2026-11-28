package com.voidlauncher.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.voidlauncher.app.data.launch.TouchControl
import com.voidlauncher.app.data.launch.TouchControls
import com.voidlauncher.app.data.launch.TouchGeometry
import com.voidlauncher.app.ui.game.TouchControlGlyph
import kotlin.math.roundToInt

/**
 * Lets the player drag each on-screen control to a new place. The preview is the whole screen, so positions are
 * fractions of it, the same as in the game. Changes stay in a draft until Save. Cancel discards them, and Reset
 * returns every control to its default position for this screen shape.
 */
@Composable
fun TouchLayoutEditor(onDone: () -> Unit) {
    val settings by TouchControls.settings.collectAsState()
    val profile by TouchControls.profile.collectAsState()
    val saved by TouchControls.layout.collectAsState()
    var draft by remember(profile) { mutableStateOf(saved) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    // The layout is kept per screen shape, and the game has not necessarily run yet, so the shape comes from the
    // display. The aspect ratio is the same in dp as in pixels, so the layout key matches the game's.
    LaunchedEffect(configuration.screenWidthDp, configuration.screenHeightDp) {
        TouchControls.setScreenSize(configuration.screenWidthDp, configuration.screenHeightDp)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Button layout",
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface,
        )
        Text(
            text = "Drag each control where it suits your thumbs. The layout is saved for screens shaped like this one ($profile).",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceVariant.copy(alpha = 0.35f))
                .onSizeChanged { area = it },
        ) {
            if (area.width > 0 && area.height > 0) {
                for (control in TouchControl.entries) {
                    val (fx, fy) = draft[control] ?: (control.defaultX to control.defaultY)
                    val radius = TouchGeometry.radiusPx(control, settings.scale, area.width, area.height)
                    val centerX = fx * area.width
                    val centerY = fy * area.height
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((centerX - radius).roundToInt(), (centerY - radius).roundToInt()) }
                            .pointerInput(control) {
                                detectDragGestures(
                                    onDrag = { _, amount ->
                                        val (ox, oy) = draft[control] ?: (control.defaultX to control.defaultY)
                                        val nx = TouchGeometry.clampPosition(ox + amount.x / area.width)
                                        val ny = TouchGeometry.clampPosition(oy + amount.y / area.height)
                                        draft = draft + (control to (nx to ny))
                                    },
                                )
                            },
                    ) {
                        TouchControlGlyph(
                            label = control.label,
                            diameter = with(density) { (radius * 2f).toDp() },
                            opacity = settings.opacity,
                            highlighted = false,
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                TouchControls.resetLayout(profile)
                draft = TouchControls.defaultLayout()
            }) {
                Text("Reset")
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onDone) {
                Text("Cancel")
            }
            Button(onClick = {
                TouchControls.saveLayout(profile, draft)
                onDone()
            }) {
                Text("Save")
            }
        }
    }
}

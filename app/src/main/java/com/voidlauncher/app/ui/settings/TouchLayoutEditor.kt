package com.voidlauncher.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.voidlauncher.app.data.launch.ButtonKind
import com.voidlauncher.app.data.launch.TouchButton
import com.voidlauncher.app.data.launch.TouchControls
import com.voidlauncher.app.data.launch.TouchGeometry
import com.voidlauncher.app.data.launch.TouchLayoutFormatException
import com.voidlauncher.app.data.launch.TouchLayouts
import com.voidlauncher.app.data.launch.TouchLayoutJson
import com.voidlauncher.app.ui.game.TouchControlGlyph
import java.io.IOException
import kotlin.math.roundToInt

/**
 * The layout editor. The preview is the whole screen, so each button's position is shown as it is in the game.
 * Tap a control to select it and edit it below, and drag it to move it. Add creates a button or joystick, and Delete
 * removes the selected one. Import and Export use the system file picker, with the same JSON the app saves.
 *
 * Changes stay in a draft until Save. Cancel discards them. Default replaces the draft with Mojo's default layout.
 */
@Composable
fun TouchLayoutEditor(onDone: () -> Unit) {
    val context = LocalContext.current
    val settings by TouchControls.settings.collectAsState()
    val profile by TouchControls.profile.collectAsState()
    val saved by TouchControls.layout.collectAsState()
    var draft by remember(profile) { mutableStateOf(saved) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var message by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    // The layout is kept per screen shape, and the game has not necessarily run yet, so the shape comes from the
    // display. The aspect ratio is the same in dp as in pixels, so the layout key matches the game's.
    LaunchedEffect(configuration.screenWidthDp, configuration.screenHeightDp) {
        TouchControls.setScreenSize(configuration.screenWidthDp, configuration.screenHeightDp)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            message = try {
                val bytes = TouchLayoutJson.encode(draft).toByteArray(Charsets.UTF_8)
                val stream = context.contentResolver.openOutputStream(uri)
                    ?: throw IOException("The file could not be opened.")
                stream.use { it.write(bytes) }
                "Layout exported."
            } catch (e: IOException) {
                "The layout could not be exported."
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            message = try {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("The file could not be opened.")
                val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                draft = TouchLayoutJson.decode(text)
                selectedId = null
                "Layout imported. Save to keep it."
            } catch (e: TouchLayoutFormatException) {
                e.message ?: "The file is not a valid layout."
            } catch (e: IOException) {
                "The layout could not be read."
            }
        }
    }

    fun updateButton(updated: TouchButton) {
        draft = draft.replace(updated)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Button layout",
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface,
        )
        Text(
            text = "Layout for screens shaped like this one ($profile). Tap a control to edit it, and drag it to move it.",
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
                for (button in draft.buttons) {
                    val (centreX, centreY) = TouchGeometry.centrePx(button, area.width, area.height)
                    val radius = TouchGeometry.radiusPx(button, settings.scale, area.width, area.height)
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((centreX - radius).roundToInt(), (centreY - radius).roundToInt()) }
                            .clickable { selectedId = button.id }
                            .pointerInput(button.id) {
                                detectDragGestures(
                                    onDragStart = { selectedId = button.id },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        val current = draft.button(button.id)
                                        if (current != null) {
                                            val (fx, fy) = TouchGeometry.centreFraction(current)
                                            val anchor = TouchGeometry.anchorFor(
                                                (fx + amount.x / area.width).coerceIn(0f, 1f),
                                                (fy + amount.y / area.height).coerceIn(0f, 1f),
                                            )
                                            updateButton(
                                                current.copy(
                                                    anchorRight = anchor.right,
                                                    anchorBottom = anchor.bottom,
                                                    x = anchor.x,
                                                    y = anchor.y,
                                                ),
                                            )
                                        }
                                    },
                                )
                            },
                    ) {
                        TouchControlGlyph(
                            label = if (button.isJoystick) "" else button.name,
                            diameter = with(density) { (radius * 2f).toDp() },
                            opacity = settings.opacity * button.opacity,
                            highlighted = button.id == selectedId,
                            fillArgb = button.background,
                            stroke = button.stroke,
                            cornerRadius = if (button.isJoystick) 1f else button.cornerRadius,
                        )
                    }
                }
            }
        }

        val selected = selectedId?.let { draft.button(it) }
        if (selected != null) {
            TouchButtonInspector(
                button = selected,
                onChange = { updateButton(it) },
                onDelete = {
                    draft = draft.remove(selected.id)
                    selectedId = null
                },
                modifier = Modifier.weight(1f),
            )
        }

        message?.let { text ->
            Text(text = text, style = MaterialTheme.typography.bodySmall, color = colors.primary)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showAdd = true }) {
                Text("Add")
            }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) }) {
                Text("Import")
            }
            OutlinedButton(onClick = { exportLauncher.launch("void-touch-layout-$profile.json") }) {
                Text("Export")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                draft = TouchControls.defaultLayout()
                selectedId = null
                message = "Default layout restored. Save to keep it."
            }) {
                Text("Default")
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

    if (showAdd) {
        AddControlDialog(
            onAdd = { kind ->
                showAdd = false
                if (draft.buttons.size >= TouchLayouts.MAX_BUTTONS) {
                    message = "A layout can have at most ${TouchLayouts.MAX_BUTTONS} controls."
                } else {
                    val id = TouchLayouts.newId(draft.buttons.map { it.id }.toSet())
                    val number = draft.buttons.size + 1
                    val button = if (kind == ButtonKind.JOYSTICK) {
                        TouchButton(id = id, name = "Move", kind = ButtonKind.JOYSTICK, x = 0.5f, y = 0.5f)
                    } else {
                        TouchButton(id = id, name = "Button $number", x = 0.5f, y = 0.5f)
                    }
                    draft = draft.add(button)
                    selectedId = id
                }
            },
            onDismiss = { showAdd = false },
        )
    }
}

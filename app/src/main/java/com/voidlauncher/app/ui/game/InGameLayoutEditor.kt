package com.voidlauncher.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.voidlauncher.app.data.launch.ButtonKind
import com.voidlauncher.app.data.launch.TouchButton
import com.voidlauncher.app.data.launch.TouchControls
import com.voidlauncher.app.data.launch.TouchGeometry
import com.voidlauncher.app.data.launch.TouchLayouts
import com.voidlauncher.app.ui.settings.TouchButtonInspector
import kotlin.math.roundToInt

/**
 * The layout editor, drawn over the game screen. Every control is drawn where the game draws it, so it is edited in
 * place. Tap a control to select it, and drag it to move it. The bar at the bottom adds, copies, deletes and edits the
 * selected control, and saves the layout that is in use.
 *
 * Changes are a draft until Save. Exit asks before it throws changes away. The game's touch routing is off while this is
 * open, so a drag here never presses a control in the game.
 */
@Composable
fun InGameLayoutEditor(onExit: () -> Unit) {
    val settings by TouchControls.settings.collectAsState()
    val saved by TouchControls.layout.collectAsState()
    val activeName by TouchControls.activeName.collectAsState()
    var draft by remember { mutableStateOf(saved) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var message by remember { mutableStateOf<String?>(null) }
    var showProperties by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val selected = selectedId?.let { draft.button(it) }
    val dirty = draft != saved

    fun updateButton(updated: TouchButton) {
        draft = draft.replace(updated)
    }

    /** Adds a new control in the middle of the screen, nothing bound, and selects it. */
    fun addControl(kind: ButtonKind) {
        if (draft.buttons.size >= TouchLayouts.MAX_BUTTONS) {
            message = "A layout can have at most ${TouchLayouts.MAX_BUTTONS} controls."
            return
        }
        val id = TouchLayouts.newId(draft.buttons.map { it.id }.toSet())
        val button = if (kind == ButtonKind.JOYSTICK) {
            TouchButton(id = id, name = "Move", kind = ButtonKind.JOYSTICK, x = 0.5f, y = 0.5f)
        } else {
            TouchButton(id = id, name = "Button ${draft.buttons.size + 1}", x = 0.5f, y = 0.5f)
        }
        draft = draft.add(button)
        selectedId = id
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x77000000))
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

        // Top bar: the layout's name, what to do next or the last message, and Exit.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color(0xCC000000))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Editing \"$activeName\"${if (dirty) " (unsaved)" else ""}",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
                Text(
                    text = message ?: "Tap a control to select it. Drag it to move it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (message != null) colors.primary else Color.White,
                )
            }
            TextButton(onClick = { if (dirty) confirmExit = true else onExit() }) {
                Text("Exit", color = Color.White)
            }
        }

        // Bottom bar: the actions. It scrolls sideways on narrow screens.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xB3000000))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(onClick = { addControl(ButtonKind.BUTTON) }) {
                Text("Add button")
            }
            OutlinedButton(onClick = { addControl(ButtonKind.JOYSTICK) }) {
                Text("Add joystick")
            }
            OutlinedButton(
                onClick = { showProperties = true },
                enabled = selected != null,
            ) {
                Text("Properties")
            }
            OutlinedButton(
                onClick = {
                    if (selected != null) {
                        if (draft.buttons.size >= TouchLayouts.MAX_BUTTONS) {
                            message = "A layout can have at most ${TouchLayouts.MAX_BUTTONS} controls."
                        } else {
                            val id = TouchLayouts.newId(draft.buttons.map { it.id }.toSet())
                            val copy = selected.copy(
                                id = id,
                                name = TouchLayouts.cleanName("${selected.name} copy"),
                                x = (selected.x + 0.04f).coerceIn(TouchGeometry.MIN_POSITION, TouchGeometry.MAX_POSITION),
                                y = (selected.y + 0.04f).coerceIn(TouchGeometry.MIN_POSITION, TouchGeometry.MAX_POSITION),
                            )
                            draft = draft.add(copy)
                            selectedId = id
                        }
                    }
                },
                enabled = selected != null,
            ) {
                Text("Clone")
            }
            OutlinedButton(
                onClick = {
                    if (selected != null) {
                        draft = draft.remove(selected.id)
                        selectedId = null
                        showProperties = false
                    }
                },
                enabled = selected != null,
            ) {
                Text("Delete")
            }
            OutlinedButton(onClick = {
                TouchControls.saveActive(draft)
                message = "Saved."
            }) {
                Text("Save")
            }
            OutlinedButton(onClick = {
                TouchControls.saveActive(draft)
                onExit()
            }) {
                Text("Save and exit")
            }
        }
    }

    if (showProperties && selected != null) {
        AlertDialog(
            onDismissRequest = { showProperties = false },
            title = { Text("Properties") },
            text = {
                TouchButtonInspector(
                    button = selected,
                    onChange = { updateButton(it) },
                    onDelete = {
                        draft = draft.remove(selected.id)
                        selectedId = null
                        showProperties = false
                    },
                    modifier = Modifier.heightIn(max = 460.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = { showProperties = false }) {
                    Text("Done")
                }
            },
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Discard changes?") },
            text = { Text("The layout will go back to how it was when you opened the editor.") },
            confirmButton = {
                TextButton(onClick = onExit) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) {
                    Text("Keep editing")
                }
            },
        )
    }
}

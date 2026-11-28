package com.voidlauncher.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.voidlauncher.app.data.launch.Binding
import com.voidlauncher.app.data.launch.BindingChoices
import com.voidlauncher.app.data.launch.ButtonKind
import com.voidlauncher.app.data.launch.TouchButton
import com.voidlauncher.app.data.launch.TouchLayouts
import com.voidlauncher.app.data.launch.label
import kotlin.math.roundToInt

/**
 * The editor for one button: its name, what it sends, how it behaves and how it looks. Every change is handed to
 * [onChange] at once, and the editor keeps the result as a draft until Save.
 */
@Composable
fun TouchButtonInspector(
    button: TouchButton,
    onChange: (TouchButton) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickingBinding by remember(button.id) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = button.name,
            onValueChange = { onChange(button.copy(name = it.take(TouchLayouts.MAX_NAME_LENGTH))) },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (!button.isJoystick) {
            Text("Sends", style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            if (button.bindings.isEmpty()) {
                Text(
                    text = "Nothing yet. Add a key or mouse button below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            for ((index, binding) in button.bindings.withIndex()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = binding.label(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        onChange(button.copy(bindings = button.bindings.filterIndexed { i, _ -> i != index }))
                    }) {
                        Text("Remove")
                    }
                }
            }
            OutlinedButton(
                onClick = { pickingBinding = true },
                enabled = button.bindings.size < TouchLayouts.MAX_BINDINGS,
            ) {
                Text("Add key or button (${button.bindings.size}/${TouchLayouts.MAX_BINDINGS})")
            }
            SwitchRow(
                label = "Tap to switch",
                description = "Each tap switches the bindings on or off, like Mojo's Shift toggle.",
                checked = button.toggle,
                onChange = { onChange(button.copy(toggle = it)) },
            )
            SwitchRow(
                label = "Slide over",
                description = "A finger that slides off this button presses the next slide button it reaches.",
                checked = button.swipe,
                onChange = { onChange(button.copy(swipe = it)) },
            )
        } else {
            SwitchRow(
                label = "Forward lock",
                description = "Pull the stick past its edge to hold Ctrl (sprint). Push it back to let go.",
                checked = button.forwardLock,
                onChange = { onChange(button.copy(forwardLock = it)) },
            )
            SwitchRow(
                label = "Base follows the finger",
                description = "The base moves to where the finger lands, instead of staying in place.",
                checked = button.absolute,
                onChange = { onChange(button.copy(absolute = it)) },
            )
        }

        Text("Shown", style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
        SwitchRow(
            label = "In the world",
            description = null,
            checked = button.inWorld,
            onChange = { onChange(button.copy(inWorld = it)) },
        )
        SwitchRow(
            label = "In menus",
            description = null,
            checked = button.inMenu,
            onChange = { onChange(button.copy(inMenu = it)) },
        )

        Text("Size and look", style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
        LabelledSlider(
            label = "Size",
            value = button.size,
            range = 0.5f..2f,
            display = "${(button.size * 100f).roundToInt()}%",
            onChange = { onChange(button.copy(size = it)) },
        )
        LabelledSlider(
            label = "Opacity",
            value = button.opacity,
            range = 0.2f..1f,
            display = "${(button.opacity * 100f).roundToInt()}%",
            onChange = { onChange(button.copy(opacity = it)) },
        )
        if (!button.isJoystick) {
            LabelledSlider(
                label = "Corners",
                value = button.cornerRadius,
                range = 0f..1f,
                display = if (button.cornerRadius >= 0.99f) "Round" else "${(button.cornerRadius * 100f).roundToInt()}%",
                onChange = { onChange(button.copy(cornerRadius = it)) },
            )
        }
        SwatchRow(
            label = "Fill",
            presets = TouchLayouts.BACKGROUND_PRESETS,
            selected = button.background,
            onPick = { onChange(button.copy(background = it)) },
        )
        SwatchRow(
            label = "Outline",
            presets = TouchLayouts.STROKE_PRESETS,
            selected = button.stroke,
            onPick = { onChange(button.copy(stroke = it)) },
        )

        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(onClick = onDelete) {
            Text("Delete this control")
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (pickingBinding) {
        BindingPickerDialog(
            choices = BindingChoices.all.filter { it !in button.bindings },
            onPick = { binding ->
                onChange(button.copy(bindings = button.bindings + binding))
                pickingBinding = false
            },
            onDismiss = { pickingBinding = false },
        )
    }
}

/** The dialog that adds a key, mouse button or scroll binding to a button. */
@Composable
private fun BindingPickerDialog(
    choices: List<Binding>,
    onPick: (Binding) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a key or button") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(choices) { choice ->
                    TextButton(onClick = { onPick(choice) }, modifier = Modifier.fillMaxWidth()) {
                        Text(choice.label(), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/** The dialog that adds a new control to the layout. */
@Composable
fun AddControlDialog(
    onAdd: (ButtonKind) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a control") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "A new button starts with nothing bound. Pick its keys in the editor. A joystick moves with W, A, S and D.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { onAdd(ButtonKind.BUTTON) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Button")
                }
                OutlinedButton(onClick = { onAdd(ButtonKind.JOYSTICK) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Joystick")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SwitchRow(
    label: String,
    description: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LabelledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(display, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SwatchRow(
    label: String,
    presets: List<Long>,
    selected: Long,
    onPick: (Long) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (preset in presets) {
                val chosen = preset == selected
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(preset))
                        .border(
                            width = if (chosen) 3.dp else 1.dp,
                            color = if (chosen) colors.primary else colors.outline,
                            shape = CircleShape,
                        )
                        .clickable { onPick(preset) },
                ) {}
            }
        }
    }
}

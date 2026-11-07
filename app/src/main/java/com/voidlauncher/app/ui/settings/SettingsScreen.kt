package com.voidlauncher.app.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voidlauncher.app.ui.components.VoidCard
import com.voidlauncher.app.ui.components.VoidTab
import com.voidlauncher.app.ui.components.VoidTabRow
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val tabs = listOf(
        VoidTab("Video", Icons.Filled.Image),
        VoidTab("Controls", Icons.Filled.Gamepad),
        VoidTab("Game", Icons.Filled.Star),
        VoidTab("Launcher", Icons.Filled.Settings),
        VoidTab("Experimental", Icons.Filled.Warning),
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onBackground,
        )
        VoidTabRow(tabs = tabs, selectedIndex = tab, onSelect = { tab = it })

        AnimatedContent(
            targetState = tab,
            label = "settings-tab",
            transitionSpec = {
                (fadeIn() + slideInHorizontally(initialOffsetX = { it / 8 })) togetherWith fadeOut()
            },
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            VoidCard(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (current) {
                    0 -> {
                        SettingSlider(
                            title = "Resolution scale",
                            description = "Lower values run faster on weaker phones.",
                            value = state.resolutionScale,
                            range = 0.5f..1f,
                            valueText = "${(state.resolutionScale * 100).roundToInt()}%",
                            onChange = { value -> viewModel.edit { it.copy(resolutionScale = value) } },
                        )
                        SettingSwitch(
                            title = "Fullscreen",
                            description = "Hide the status bar while playing.",
                            checked = state.fullscreen,
                            onChange = { value -> viewModel.edit { it.copy(fullscreen = value) } },
                        )
                        SettingSwitch(
                            title = "V-Sync",
                            description = "Syncs frames to the screen to reduce tearing.",
                            checked = state.vsync,
                            onChange = { value -> viewModel.edit { it.copy(vsync = value) } },
                        )
                    }
                    1 -> {
                        SettingSwitch(
                            title = "Disable gestures",
                            description = "Turn off long press to break and tap to place.",
                            checked = state.disableGestures,
                            onChange = { value -> viewModel.edit { it.copy(disableGestures = value) } },
                        )
                        SettingSlider(
                            title = "Control scale",
                            description = "Make on-screen buttons larger or smaller.",
                            value = state.controlScale,
                            range = 0.5f..1.5f,
                            valueText = "${(state.controlScale * 100).roundToInt()}%",
                            onChange = { value -> viewModel.edit { it.copy(controlScale = value) } },
                        )
                    }
                    2 -> {
                        SettingSlider(
                            title = "Maximum memory",
                            description = "How much RAM Minecraft may use.",
                            value = state.maxRamGb,
                            range = 1f..8f,
                            valueText = "${state.maxRamGb.roundToInt()} GB",
                            onChange = { value -> viewModel.edit { it.copy(maxRamGb = value) } },
                        )
                    }
                    3 -> {
                        SettingSwitch(
                            title = "Animated background",
                            description = "Turn off to save battery.",
                            checked = state.showAnimations,
                            onChange = { value -> viewModel.edit { it.copy(showAnimations = value) } },
                        )
                    }
                    else -> {
                        SettingSwitch(
                            title = "Vulkan driver (experimental)",
                            description = "May crash on some devices. Try it only if the default fails.",
                            checked = state.vulkanDriver,
                            onChange = { value -> viewModel.edit { it.copy(vulkanDriver = value) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingSlider(
    title: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium,
                color = colors.primary,
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
        )
    }
}

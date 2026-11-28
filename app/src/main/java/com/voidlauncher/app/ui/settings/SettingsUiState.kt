package com.voidlauncher.app.ui.settings

import com.voidlauncher.app.data.launch.GlRenderer

data class SettingsUiState(
    val resolutionScale: Float = 1f,
    val fullscreen: Boolean = true,
    val vsync: Boolean = false,
    val controlScale: Float = 1f,
    val touchControls: Boolean = true,
    val touchOpacity: Float = 0.6f,
    val disableGestures: Boolean = false,
    val touchSensitivity: Float = 1f,
    val touchpadMode: Boolean = false,
    val maxRamGb: Float = 4f,
    val showAnimations: Boolean = true,
    val vulkanDriver: Boolean = false,
    val glRenderer: GlRenderer = GlRenderer.DEFAULT,
)

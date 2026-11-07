package com.voidlauncher.app.ui.settings

data class SettingsUiState(
    val resolutionScale: Float = 1f,
    val fullscreen: Boolean = true,
    val vsync: Boolean = false,
    val controlScale: Float = 1f,
    val disableGestures: Boolean = false,
    val maxRamGb: Float = 4f,
    val showAnimations: Boolean = true,
    val vulkanDriver: Boolean = false,
)

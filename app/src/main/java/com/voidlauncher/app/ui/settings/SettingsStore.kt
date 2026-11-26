package com.voidlauncher.app.ui.settings

import com.voidlauncher.app.data.AppPrefs

/**
 * Saves and loads the settings screen values through [AppPrefs]. A missing value uses its default,
 * and loaded numbers are clamped to the range their slider allows.
 */
object SettingsStore {

    fun load(): SettingsUiState {
        val d = SettingsUiState()
        return SettingsUiState(
            resolutionScale = AppPrefs.float("resolutionScale", d.resolutionScale).coerceIn(0.5f, 1f),
            fullscreen = AppPrefs.bool("fullscreen", d.fullscreen),
            vsync = AppPrefs.bool("vsync", d.vsync),
            controlScale = AppPrefs.float("controlScale", d.controlScale).coerceIn(0.5f, 1.5f),
            disableGestures = AppPrefs.bool("disableGestures", d.disableGestures),
            touchSensitivity = AppPrefs.float("touchSensitivity", d.touchSensitivity).coerceIn(0.25f, 3f),
            touchpadMode = AppPrefs.bool("touchpadMode", d.touchpadMode),
            maxRamGb = AppPrefs.float("maxRamGb", d.maxRamGb).coerceIn(1f, 8f),
            showAnimations = AppPrefs.bool("showAnimations", d.showAnimations),
            vulkanDriver = AppPrefs.bool("vulkanDriver", d.vulkanDriver),
        )
    }

    fun save(state: SettingsUiState) {
        AppPrefs.edit { editor ->
            editor.putFloat("resolutionScale", state.resolutionScale)
            editor.putBoolean("fullscreen", state.fullscreen)
            editor.putBoolean("vsync", state.vsync)
            editor.putFloat("controlScale", state.controlScale)
            editor.putBoolean("disableGestures", state.disableGestures)
            editor.putFloat("touchSensitivity", state.touchSensitivity)
            editor.putBoolean("touchpadMode", state.touchpadMode)
            editor.putFloat("maxRamGb", state.maxRamGb)
            editor.putBoolean("showAnimations", state.showAnimations)
            editor.putBoolean("vulkanDriver", state.vulkanDriver)
        }
    }
}

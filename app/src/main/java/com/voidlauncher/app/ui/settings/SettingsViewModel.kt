package com.voidlauncher.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.voidlauncher.app.data.AppPrefs
import com.voidlauncher.app.data.launch.GameAction
import com.voidlauncher.app.data.launch.GameInputSettings
import com.voidlauncher.app.data.launch.GameKeybinds
import com.voidlauncher.app.data.launch.TouchControls
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Settings and keybinds. Every change is saved at once and pushed to the game input.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsStore.load())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _keybinds = MutableStateFlow(AppPrefs.keybinds())
    val keybinds: StateFlow<Map<GameAction, Int>> = _keybinds.asStateFlow()

    init {
        pushInputSettings(_uiState.value)
        GameKeybinds.update(_keybinds.value)
    }

    fun edit(transform: (SettingsUiState) -> SettingsUiState) {
        _uiState.update(transform)
        SettingsStore.save(_uiState.value)
        pushInputSettings(_uiState.value)
    }

    /**
     * Binds [keyCode] to [action]. Returns an error message if the key cannot be used, or null once it is saved.
     * A key can belong to only one action, and system keys cannot be bound.
     */
    fun bindKey(action: GameAction, keyCode: Int): String? {
        if (keyCode in GameKeybinds.reservedKeys) {
            return "${GameKeybinds.keyName(keyCode)} is reserved by Android."
        }
        val owner = _keybinds.value.entries.firstOrNull { it.value == keyCode && it.key != action }?.key
        if (owner != null) {
            return "${GameKeybinds.keyName(keyCode)} is already used for ${owner.label}."
        }
        saveKeybinds(_keybinds.value + (action to keyCode))
        return null
    }

    fun resetKeybinds() {
        saveKeybinds(GameAction.entries.associateWith { it.defaultKeyCode })
    }

    private fun saveKeybinds(bindings: Map<GameAction, Int>) {
        _keybinds.value = bindings
        AppPrefs.saveKeybinds(bindings)
        GameKeybinds.update(bindings)
    }

    // The game screen reads touch options from GameInputSettings and the on-screen controls from TouchControls,
    // so every change is pushed to both.
    private fun pushInputSettings(state: SettingsUiState) {
        GameInputSettings.update(state.touchSensitivity, state.touchpadMode, state.disableGestures)
        TouchControls.applySettings(state.touchControls, state.controlScale, state.touchOpacity)
    }
}

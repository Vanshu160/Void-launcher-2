package com.voidlauncher.app.ui.settings

import androidx.lifecycle.ViewModel
import com.voidlauncher.app.data.launch.GameInputSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Values are kept in memory for now. Saving them to storage comes in the Settings milestone.
class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        pushInputSettings(_uiState.value)
    }

    fun edit(transform: (SettingsUiState) -> SettingsUiState) {
        _uiState.update(transform)
        pushInputSettings(_uiState.value)
    }

    // The game screen reads touch options from GameInputSettings, so every change is pushed there.
    private fun pushInputSettings(state: SettingsUiState) {
        GameInputSettings.update(state.touchSensitivity, state.touchpadMode, state.disableGestures)
    }
}

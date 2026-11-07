package com.voidlauncher.app.ui.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Values are kept in memory for now. Saving them to storage comes in the Settings milestone.
class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun edit(transform: (SettingsUiState) -> SettingsUiState) {
        _uiState.update(transform)
    }
}

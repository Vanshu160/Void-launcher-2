package com.voidlauncher.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voidlauncher.app.data.SelectedVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            SelectedVersion.flow.collect { version ->
                _uiState.update {
                    it.copy(
                        selectedVersion = version,
                        statusMessage = if (version == null) {
                            "Choose a version to get started."
                        } else {
                            "Game files download in a later milestone."
                        },
                    )
                }
            }
        }
    }
}

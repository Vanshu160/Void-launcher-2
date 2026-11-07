package com.voidlauncher.app.ui.versions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voidlauncher.app.data.VersionRepository
import com.voidlauncher.app.data.local.VersionCache
import com.voidlauncher.app.domain.model.VersionFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VersionsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VersionRepository(VersionCache(application))

    private val _uiState = MutableStateFlow(VersionsUiState())
    val uiState: StateFlow<VersionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Show the saved list straight away, then try to update it.
            repository.loadCached()?.let { cached ->
                _uiState.update { it.copy(versions = cached, fromCache = true) }
            }
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val result = repository.refresh()
                _uiState.update {
                    it.copy(
                        versions = result.versions,
                        fromCache = result.fromCache,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Couldn't load versions. Check your connection and try again.",
                    )
                }
            }
        }
    }

    fun setFilter(filter: VersionFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun select(id: String) {
        _uiState.update { it.copy(selectedId = id) }
    }
}

package com.voidlauncher.app.ui.mods

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voidlauncher.app.data.ModRepository
import com.voidlauncher.app.data.SelectedVersion
import com.voidlauncher.app.domain.model.ModSearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class ModsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ModRepository(File(application.filesDir, "minecraft"))

    private val _uiState = MutableStateFlow(ModsUiState())
    val uiState: StateFlow<ModsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            SelectedVersion.flow.collect { version ->
                _uiState.update { it.copy(gameVersion = version, results = emptyList()) }
                reloadInstalled()
            }
        }
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun search() {
        val version = _uiState.value.gameVersion ?: return
        val query = _uiState.value.query.trim()
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, message = null) }
            try {
                val results = repository.search(query, version)
                _uiState.update { it.copy(results = results, isSearching = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSearching = false, message = "Couldn't reach Modrinth. Check your connection and try again.")
                }
            }
        }
    }

    fun install(result: ModSearchResult) {
        val version = _uiState.value.gameVersion ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(installingProjectId = result.projectId, message = null) }
            try {
                val saved = repository.install(result.projectId, version)
                val extra = saved.size - 1
                val suffix = if (extra > 0) " plus $extra required file${if (extra == 1) "" else "s"}" else ""
                _uiState.update {
                    it.copy(installingProjectId = null, message = "Installed ${result.title}$suffix.")
                }
                reloadInstalled()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(installingProjectId = null, message = "Couldn't install ${result.title}. ${e.message.orEmpty()}")
                }
            }
        }
    }

    fun remove(fileName: String) {
        val version = _uiState.value.gameVersion ?: return
        viewModelScope.launch {
            repository.remove(version, fileName)
            reloadInstalled()
        }
    }

    private suspend fun reloadInstalled() {
        val version = _uiState.value.gameVersion
        val installed = if (version == null) emptyList() else repository.installed(version)
        _uiState.update { it.copy(installed = installed) }
    }
}

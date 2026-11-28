package com.voidlauncher.app.ui.mods

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voidlauncher.app.data.AppPrefs
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

    private val _uiState = MutableStateFlow(ModsUiState(loader = AppPrefs.modLoader()))
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

    /** Picks the mod loader Modrinth filters by. The choice is saved and the old results are cleared. */
    fun setLoader(loader: String) {
        if (loader !in ModRepository.LOADERS || loader == _uiState.value.loader) return
        AppPrefs.saveModLoader(loader)
        _uiState.update { it.copy(loader = loader, results = emptyList(), message = null) }
    }

    fun search() {
        val state = _uiState.value
        val version = state.gameVersion ?: return
        val query = state.query.trim()
        val loader = state.loader
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, message = null) }
            try {
                val results = repository.search(query, version, loader)
                _uiState.update { it.copy(results = results, isSearching = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSearching = false, message = "Couldn't reach Modrinth. Check your connection and try again.")
                }
            }
        }
    }

    fun install(result: ModSearchResult) {
        val state = _uiState.value
        val version = state.gameVersion ?: return
        val loader = state.loader
        viewModelScope.launch {
            _uiState.update { it.copy(installingProjectId = result.projectId, message = null) }
            try {
                val install = repository.install(result.projectId, version, loader)
                val extra = install.savedFiles.size - 1
                val suffix = if (extra > 0) " plus $extra required file${if (extra == 1) "" else "s"}" else ""
                val skipped = install.skippedDependencies
                val skipNote = if (skipped > 0) {
                    " $skipped required dependenc${if (skipped == 1) "y" else "ies"} had no file for this version and loader, so ${if (skipped == 1) "it was" else "they were"} skipped."
                } else {
                    ""
                }
                _uiState.update {
                    it.copy(installingProjectId = null, message = "Installed ${result.title}$suffix.$skipNote")
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

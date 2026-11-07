package com.voidlauncher.app.ui.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voidlauncher.app.data.SelectedVersion
import com.voidlauncher.app.data.VersionRepository
import com.voidlauncher.app.data.download.GameDownloader
import com.voidlauncher.app.data.local.VersionCache
import com.voidlauncher.app.data.remote.GameMetadataParser
import com.voidlauncher.app.data.runtime.JavaRuntimeManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val minecraftRoot = File(application.filesDir, "minecraft")
    private val downloader = GameDownloader(minecraftRoot)
    private val runtimes = JavaRuntimeManager(application)
    private val versions = VersionRepository(VersionCache(application))

    private val _uiState = MutableStateFlow(DownloadsUiState(selectedId = SelectedVersion.flow.value))
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch {
            SelectedVersion.flow.collect { id ->
                _uiState.update { it.copy(selectedId = id) }
            }
        }
    }

    fun startDownload() {
        val id = _uiState.value.selectedId ?: return
        if (job?.isActive == true) return

        job = viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, progress = null, message = null, isError = false) }
            try {
                val version = versions.loadCached()?.firstOrNull { it.id == id }
                    ?: throw IllegalStateException("Open the Versions tab first so the version list is saved.")

                // 1. Game files (metadata, client, libraries, assets).
                downloader.download(version) { progress ->
                    _uiState.update { it.copy(progress = progress) }
                }

                // 2. The Java runtime this version needs.
                val metadata = GameMetadataParser.parse(
                    File(minecraftRoot, "versions/$id/$id.json").readText(),
                )
                runtimes.install(metadata.javaMajorVersion) { stage ->
                    _uiState.update { it.copy(progress = null, message = stage, isError = false) }
                }

                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        message = "Minecraft $id and Java ${metadata.javaMajorVersion} are ready on this device.",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isDownloading = false, isError = true, message = e.message ?: "Download failed.")
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _uiState.update {
            it.copy(isDownloading = false, message = "Download cancelled. Start again to resume.")
        }
    }
}

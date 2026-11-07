package com.voidlauncher.app.ui.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voidlauncher.app.data.SelectedVersion
import com.voidlauncher.app.data.VersionRepository
import com.voidlauncher.app.data.download.GameDownloader
import com.voidlauncher.app.data.launch.GameLauncher
import com.voidlauncher.app.data.local.VersionCache
import com.voidlauncher.app.data.log.AppLog
import com.voidlauncher.app.data.remote.GameMetadataParser
import com.voidlauncher.app.data.runtime.JavaRuntimeManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val minecraftRoot = File(application.filesDir, "minecraft")
    private val downloader = GameDownloader(minecraftRoot)
    private val runtimes = JavaRuntimeManager(application)
    private val launcher = GameLauncher(application)
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

                downloader.download(version) { progress ->
                    _uiState.update { it.copy(progress = progress) }
                }

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
                AppLog.write("Download failed: ${e.message}")
                _uiState.update {
                    it.copy(isDownloading = false, isError = true, message = e.message ?: "Download failed.")
                }
            }
        }
    }

    /** First launch test. Every step is written to the launch log. */
    fun startGame() {
        val id = _uiState.value.selectedId ?: return
        if (_uiState.value.isLaunching || _uiState.value.isDownloading) return

        AppLog.write("Start game tapped for $id")
        _uiState.update { it.copy(isLaunching = true, isError = false, message = "Preparing launch...") }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    launcher.launch(id) { status ->
                        AppLog.write(status)
                        _uiState.update { it.copy(message = status) }
                    }
                } catch (e: Throwable) {
                    AppLog.write("Launch error: ${e.message ?: e.javaClass.simpleName}\n${e.stackTraceToString()}")
                    _uiState.update {
                        it.copy(isError = true, message = e.message ?: e.javaClass.simpleName)
                    }
                } finally {
                    _uiState.update { it.copy(isLaunching = false) }
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

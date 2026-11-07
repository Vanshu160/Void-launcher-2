#!/usr/bin/env bash
# Void Launcher 0.8.5 - stop the Start button from launching the game a second time.
# The JVM can only be started once per app process. Until Minecraft exits, the button stays busy.
# Run from the ROOT of your Void-launcher-2 repository.
set -e

if [ ! -d .git ] || [ ! -f app/build.gradle.kts ]; then
  echo "ERROR: run this inside the Void-launcher-2 repository folder."
  exit 1
fi

BASE=app/src/main/java/com/voidlauncher/app
mkdir -p "$BASE/ui/downloads"

# ---------------------------------------------------------------------------
cat > "$BASE/ui/downloads/DownloadsViewModel.kt" << 'KT_EOF'
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

    /**
     * Starts the game. The button stays busy until Minecraft exits, because the JVM can only
     * be started once per app process.
     */
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
                        val finished = status == "Minecraft exited." ||
                            status.startsWith("Minecraft did not start") ||
                            status.startsWith("Launch failed")
                        _uiState.update {
                            it.copy(
                                message = status,
                                isLaunching = !finished,
                                isError = status.startsWith("Minecraft did not start") ||
                                    status.startsWith("Launch failed"),
                            )
                        }
                    }
                } catch (e: Throwable) {
                    AppLog.write("Launch error: ${e.message ?: e.javaClass.simpleName}\n${e.stackTraceToString()}")
                    _uiState.update {
                        it.copy(isLaunching = false, isError = true, message = e.message ?: e.javaClass.simpleName)
                    }
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
KT_EOF

# ---------------------------------------------------------------------------
echo
echo "Done. Files written for milestone 0.8.5 (one launch at a time)."
echo "Next:  git add . && git commit -m \"Milestone 0.8.5: keep Start busy until Minecraft exits\" && git push"

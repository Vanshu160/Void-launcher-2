#!/usr/bin/env bash
# Void Launcher 0.8.1 - on-device launch log, crash capture, and a copyable log view on the Downloads screen.
# Run from the ROOT of your Void-launcher-2 repository.
set -e

if [ ! -d .git ] || [ ! -f app/build.gradle.kts ]; then
  echo "ERROR: run this inside the Void-launcher-2 repository folder."
  exit 1
fi

BASE=app/src/main/java/com/voidlauncher/app
mkdir -p "$BASE/data/log" "$BASE/ui/downloads"

# ---------------------------------------------------------------------------
cat > "$BASE/data/log/AppLog.kt" << 'KT_EOF'
package com.voidlauncher.app.data.log

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small log kept in the app's private storage. It records launch steps and any Java crash,
 * so the text can be copied from the Downloads screen after the app closes.
 */
object AppLog {
    private const val MAX_BYTES = 200_000
    private var file: File? = null
    private val time = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun init(context: Context) {
        file = File(context.filesDir, "void-log.txt")
        installCrashHandler()
    }

    @Synchronized
    fun write(line: String) {
        val target = file ?: return
        try {
            if (target.length() > MAX_BYTES) {
                target.writeText(target.readText().takeLast(MAX_BYTES / 2))
            }
            target.appendText("${time.format(Date())}  $line\n")
        } catch (e: Exception) {
            // Logging must never crash the app.
        }
    }

    fun read(): String = try {
        file?.takeIf { it.isFile }?.readText().orEmpty()
    } catch (e: Exception) {
        ""
    }

    fun clear() {
        try {
            file?.delete()
        } catch (e: Exception) {
            // Ignore.
        }
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
            write("CRASH on thread ${thread.name}:\n$trace")
            previous?.uncaughtException(thread, error)
        }
    }
}
KT_EOF

# ---------------------------------------------------------------------------
cat > "$BASE/VoidApplication.kt" << 'KT_EOF'
package com.voidlauncher.app

import android.app.Application
import com.voidlauncher.app.data.log.AppLog

class VoidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppLog.write("App started")
    }
}
KT_EOF

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
KT_EOF

# ---------------------------------------------------------------------------
cat > "$BASE/ui/downloads/DownloadsScreen.kt" << 'KT_EOF'
package com.voidlauncher.app.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voidlauncher.app.data.log.AppLog
import com.voidlauncher.app.ui.components.VoidCard

@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val selected = state.selectedId
    var refresh by remember { mutableIntStateOf(0) }
    val logText = remember(state.message, state.isLaunching, refresh) { AppLog.read() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Game files",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onBackground,
        )

        VoidCard {
            Text(
                text = if (selected == null) "No version selected" else "Selected: Minecraft $selected",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            Text(
                text = "Downloads the client, libraries and assets. Files already on this device are checked and skipped.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface.copy(alpha = 0.7f),
            )

            if (state.isDownloading) {
                val progress = state.progress
                if (progress != null) {
                    Text(
                        text = "${progress.stage}: ${progress.done} / ${progress.total}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                    )
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            Button(
                onClick = viewModel::startDownload,
                enabled = selected != null && !state.isDownloading && !state.isLaunching,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isDownloading) "Downloading..." else "Download game files")
            }

            if (state.isDownloading) {
                OutlinedButton(
                    onClick = viewModel::cancel,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel")
                }
            }

            Button(
                onClick = viewModel::startGame,
                enabled = selected != null && !state.isDownloading && !state.isLaunching,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isLaunching) "Starting..." else "Start game (first test)")
            }

            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.isError) colors.error else colors.primary,
                )
            }
        }

        VoidCard {
            Text(
                text = "Launch log (long-press to copy)",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            SelectionContainer {
                Text(
                    text = logText.ifEmpty { "No log yet. Tap Start game to create one." },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.onSurface,
                )
            }
            OutlinedButton(
                onClick = {
                    AppLog.clear()
                    refresh++
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear log")
            }
        }
    }
}
KT_EOF

# ---------------------------------------------------------------------------
echo
echo "Done. Files written for milestone 0.8.1 (launch log)."
echo "Next:  git add . && git commit -m \"Milestone 0.8.1: launch log and crash capture\" && git push"

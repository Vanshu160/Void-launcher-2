#!/usr/bin/env bash
# Void Launcher 0.8.0 - first launch attempt through the PojavLauncher native hooks.
# Adds a minimal launcher. Expect the first run to stop early: native LWJGL libraries are not extracted yet.
# Run from the ROOT of your Void-launcher-2 repository.
set -e

if [ ! -d .git ] || [ ! -f app/build.gradle.kts ]; then
  echo "ERROR: run this inside the Void-launcher-2 repository folder."
  exit 1
fi

JAVA=app/src/main/java
mkdir -p "$JAVA/net/kdt/pojavlaunch/utils/jre" "$JAVA/com/voidlauncher/app/data/launch" "$JAVA/com/voidlauncher/app/ui/downloads"

# ---------------------------------------------------------------------------
# JNI bridge. The package and names must stay exactly as they are: the native code in
# libmojort.so (PojavLauncher, LGPL-3.0) looks these methods up by name.
cat > "$JAVA/net/kdt/pojavlaunch/utils/jre/JavaRunner.kt" << 'KT_EOF'
package net.kdt.pojavlaunch.utils.jre

/**
 * JNI bridge to libmojort.so (PojavLauncher native hooks, LGPL-3.0).
 * Do not rename this class or its methods: the native code finds them by name.
 */
object JavaRunner {
    @JvmStatic
    external fun nativeTransferArguments(args: Array<String>): Long

    @JvmStatic
    external fun nativeLoadJVM(
        vmPath: String,
        javaArgsL: Long,
        mainClass: String,
        appArgsL: Long,
        hasJavaAgents: Boolean,
    ): Boolean
}
KT_EOF

# ---------------------------------------------------------------------------
cat > "$JAVA/net/kdt/pojavlaunch/utils/JREUtils.kt" << 'KT_EOF'
package net.kdt.pojavlaunch.utils

/**
 * JNI bridge to libmojort.so (PojavLauncher native hooks, LGPL-3.0).
 * Do not rename this class or its methods: the native code finds them by name.
 */
object JREUtils {
    @JvmStatic
    external fun setLdLibraryPath(ldLibraryPath: String)

    @JvmStatic
    external fun chdir(path: String): Int
}
KT_EOF

# ---------------------------------------------------------------------------
cat > "$JAVA/com/voidlauncher/app/data/launch/GameLauncher.kt" << 'KT_EOF'
package com.voidlauncher.app.data.launch

import android.content.Context
import android.system.Os
import com.voidlauncher.app.data.remote.GameMetadataParser
import com.voidlauncher.app.data.runtime.JavaRuntimeManager
import com.voidlauncher.app.data.runtime.JavaRuntimeSpec
import net.kdt.pojavlaunch.utils.JREUtils
import net.kdt.pojavlaunch.utils.jre.JavaRunner
import org.json.JSONObject
import java.io.File

/**
 * Starts Minecraft the same way PojavLauncher's JavaRunner.startJvm does, with the steps
 * needed for a first launch. The JVM runs on its own thread because it does not return.
 */
class GameLauncher(private val context: Context) {

    private val minecraftRoot = File(context.filesDir, "minecraft")
    private val gameDir = File(minecraftRoot, "game")
    private val runtimes = JavaRuntimeManager(context)

    /** Loads the native libraries. Throws UnsatisfiedLinkError if they are missing from the APK. */
    private fun loadNative() {
        System.loadLibrary("mojoexec")
        System.loadLibrary("mojort")
    }

    /** Starts the game. Returns after starting the JVM thread. Progress goes to [onStatus]. */
    fun launch(versionId: String, ramMb: Int = 2048, onStatus: (String) -> Unit) {
        val metaFile = File(minecraftRoot, "versions/$versionId/$versionId.json")
        check(metaFile.isFile) { "Game files for $versionId are not downloaded." }
        val json = JSONObject(metaFile.readText())
        val metadata = GameMetadataParser.parse(metaFile.readText())

        val spec = JavaRuntimeSpec.forMajor(metadata.javaMajorVersion)
        check(runtimes.isInstalled(spec)) { "Java ${spec.majorVersion} is not installed yet." }
        val runtimeHome = runtimes.home(spec)
        val vmPath = runtimeHome.walkTopDown().firstOrNull { it.isFile && it.name == "libjvm.so" }
            ?: error("libjvm.so was not found in the Java runtime.")

        gameDir.mkdirs()
        val classpath = (listOf(File(minecraftRoot, metadata.client.relativePath)) +
            metadata.libraries.map { File(minecraftRoot, it.relativePath) })
            .joinToString(":") { it.absolutePath }

        val javaArgs = mutableListOf(
            "-Xms512M",
            "-Xmx${ramMb}M",
            "-Djava.home=${runtimeHome.absolutePath}",
            "-Djava.io.tmpdir=${context.cacheDir.absolutePath}",
            "-Duser.home=${gameDir.absolutePath}",
            "-Dpojav.path.minecraft=${minecraftRoot.absolutePath}",
            "-Dorg.lwjgl.system.allocator=system",
            "-Djava.class.path=$classpath",
        )

        val values = mapOf(
            "auth_player_name" to "Player",
            "version_name" to versionId,
            "game_directory" to gameDir.absolutePath,
            "assets_root" to File(minecraftRoot, "assets").absolutePath,
            "assets_index_name" to metadata.assetIndexId,
            "auth_uuid" to "00000000000000000000000000000000",
            "auth_access_token" to "0",
            "auth_xuid" to "0",
            "clientid" to "0",
            "user_type" to "legacy",
            "version_type" to json.optString("type", "release"),
        )
        val gameArgs = mutableListOf<String>()
        json.optJSONObject("arguments")?.optJSONArray("game")?.let { list ->
            for (i in 0 until list.length()) {
                val arg = list.opt(i)
                if (arg is String) gameArgs += substitute(arg, values)
            }
        }
        if (gameArgs.isEmpty()) {
            // Older versions use one string of arguments instead.
            json.optString("minecraftArguments").split(" ").filter { it.isNotEmpty() }.forEach {
                gameArgs += substitute(it, values)
            }
        }
        val mainClass = json.getString("mainClass")

        onStatus("Loading native libraries...")
        loadNative()

        // Same environment the PojavLauncher JVM expects.
        val libsDir = checkNotNull(vmPath.parentFile?.parentFile)
        val ldPath = listOf(
            libsDir,
            File(context.applicationInfo.nativeLibraryDir),
            vmPath.parentFile,
            File(libsDir, "jli"),
        ).joinToString(":") { it.absolutePath }
        Os.setenv("JAVA_HOME", runtimeHome.absolutePath, true)
        Os.setenv("HOME", gameDir.absolutePath, true)
        Os.setenv("TMPDIR", context.cacheDir.absolutePath, true)
        Os.setenv("LD_LIBRARY_PATH", ldPath, true)
        JREUtils.setLdLibraryPath(ldPath)

        onStatus("Starting Java ${spec.majorVersion} and Minecraft $versionId...")
        Thread(null, {
            try {
                val javaArgsPtr = JavaRunner.nativeTransferArguments(javaArgs.toTypedArray())
                val appArgsPtr = JavaRunner.nativeTransferArguments(gameArgs.toTypedArray())
                val ok = JavaRunner.nativeLoadJVM(vmPath.absolutePath, javaArgsPtr, mainClass, appArgsPtr, false)
                onStatus(if (ok) "Minecraft exited." else "Minecraft did not start.")
            } catch (e: Throwable) {
                onStatus("Launch failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }, "minecraft-jvm", 64L * 1024 * 1024).start()
    }

    private fun substitute(text: String, values: Map<String, String>): String {
        var result = text
        for ((key, value) in values) result = result.replace("\${$key}", value)
        return result
    }
}
KT_EOF

# ---------------------------------------------------------------------------
cat > "$JAVA/com/voidlauncher/app/ui/downloads/DownloadsUiState.kt" << 'KT_EOF'
package com.voidlauncher.app.ui.downloads

import com.voidlauncher.app.data.download.DownloadProgress

data class DownloadsUiState(
    val selectedId: String? = null,
    val isDownloading: Boolean = false,
    val isLaunching: Boolean = false,
    val progress: DownloadProgress? = null,
    val message: String? = null,
    val isError: Boolean = false,
)
KT_EOF

# ---------------------------------------------------------------------------
cat > "$JAVA/com/voidlauncher/app/ui/downloads/DownloadsViewModel.kt" << 'KT_EOF'
package com.voidlauncher.app.ui.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voidlauncher.app.data.SelectedVersion
import com.voidlauncher.app.data.VersionRepository
import com.voidlauncher.app.data.download.GameDownloader
import com.voidlauncher.app.data.launch.GameLauncher
import com.voidlauncher.app.data.local.VersionCache
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
                _uiState.update {
                    it.copy(isDownloading = false, isError = true, message = e.message ?: "Download failed.")
                }
            }
        }
    }

    /** First launch test. Starts the JVM and reports what happens. */
    fun startGame() {
        val id = _uiState.value.selectedId ?: return
        if (_uiState.value.isLaunching || _uiState.value.isDownloading) return

        _uiState.update { it.copy(isLaunching = true, isError = false, message = "Preparing launch...") }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    launcher.launch(id) { status ->
                        _uiState.update { it.copy(message = status) }
                    }
                } catch (e: Throwable) {
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
cat > "$JAVA/com/voidlauncher/app/ui/downloads/DownloadsScreen.kt" << 'KT_EOF'
package com.voidlauncher.app.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voidlauncher.app.ui.components.VoidCard

@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val selected = state.selectedId

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
    }
}
KT_EOF

# ---------------------------------------------------------------------------
cat >> THIRD_PARTY.md << 'MD_EOF'

## JNI bridge classes (written for Void Launcher)
- `net.kdt.pojavlaunch.utils.jre.JavaRunner` and `net.kdt.pojavlaunch.utils.JREUtils` are small Kotlin classes written for Void Launcher. Their package and method names must match the native code in libmojort.so (PojavLauncher, LGPL-3.0). No PojavLauncher Java code is copied.
- `GameLauncher` follows the steps of PojavLauncher's `JavaRunner.startJvm` (LGPL-3.0) in simplified form, rewritten in Kotlin.
MD_EOF

# ---------------------------------------------------------------------------
cat > CHECKLIST.md << 'MD_EOF'
# Void Launcher development checklist

## Milestone 0.1.0 - Foundation (complete)
- [x] Gradle skeleton, CI APK build, theme, navigation, Home screen
- [x] License: GPL-3.0 (`LICENSE`) and third-party notices (`THIRD_PARTY.md`)

## Milestone 0.2.0 - Version manager (complete)
- [x] Mojang version manifest, offline cache, release and snapshot filters

## Milestone 0.3.0 - Landscape UI overhaul (complete)
- [x] Landscape layout, animated backdrop, top tabs, Settings tabs

## Milestone 0.4.0 - Mods (Fabric via Modrinth)
- [x] Search Modrinth for Fabric mods matching the selected version
- [x] Install with SHA-1 check and required dependencies (one level)
- [x] Per-version mods folder, installed list, remove
- [x] Selected version shared between Versions, Home and Mods
- [x] CI build passes
- [ ] Verified on a phone
- [ ] Fabric loader install (needs the engine milestone)

## Milestone 0.5.0 - Game files download (complete)
- [x] Version metadata, client jar, libraries and assets downloaded with SHA-1 checks
- [x] Existing valid files are skipped, so an interrupted download can resume
- [x] Downloads screen with progress, cancel and error messages
- [x] CI build passes
- [x] Verified on a phone (Minecraft 1.21.11 downloaded)

## Milestone 0.6.0 - Java runtime (complete)
- [x] Runtime chosen from the version's required Java major (8, 17, 21, 25)
- [x] Archives checked with RSA-4096 signatures before unpacking
- [x] Java 21 downloaded and ready on device
- [ ] Runtime license verified (blocks public release)
- [ ] unpack200 step and libawt dummy (needed before the game can run fully)

## Milestone 0.7.0 - Native engine (complete in code)
- [x] 0.7.0a: NDK 29 and CMake 3.22.1 build a native library in CI
- [x] 0.7.0b: mojoexec built from a pinned commit in CI
- [x] 0.7.0c: launcher hooks (mojort) built from the pinned PojavLauncher fork in CI
- [ ] 0.7.0d: GLFW (window)
- [ ] 0.7.0e: MojoSDL
- [ ] 0.7.0f: native LWJGL libraries extracted from the game's natives

## Milestone 0.8.0 - First launch attempt (in progress)
- [x] JNI bridge classes and a minimal launcher (GameLauncher) written
- [x] "Start game (first test)" button on the Downloads screen
- [ ] CI build passes
- [ ] First run on a phone: JVM starts (result to be recorded)
- [ ] Minecraft main menu appears (needs GLFW, natives and touch input)

## Later milestones
- [ ] Settings saved between launches
- [ ] Microsoft login
- [ ] Engine API contracts
- [ ] Touch controls
- [ ] Logs and diagnostics
MD_EOF

echo
echo "Done. Files written for milestone 0.8.0 (first launch attempt)."
echo "Next:  git add . && git commit -m \"Milestone 0.8.0: first launch attempt\" && git push"

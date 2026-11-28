package com.voidlauncher.app.data.launch

import android.content.Context
import android.os.Build
import android.system.Os
import com.voidlauncher.app.NativeBridge
import com.voidlauncher.app.data.log.AppLog
import com.voidlauncher.app.data.remote.GameMetadataParser
import com.voidlauncher.app.data.runtime.JavaRuntimeManager
import com.voidlauncher.app.data.runtime.JavaRuntimeSpec
import git.artdeell.dnbootstrap.glfw.GLFW
import net.kdt.pojavlaunch.utils.JREUtils
import net.kdt.pojavlaunch.utils.jre.JavaRunner
import org.json.JSONObject
import java.io.File

/**
 * Starts Minecraft the same way PojavLauncher's JavaRunner.startJvm and GameRunner do.
 * The JVM runs on its own thread because it does not return.
 */
class GameLauncher(private val context: Context) {

    private val minecraftRoot = File(context.filesDir, "minecraft")
    private val gameDir = File(minecraftRoot, "game")
    private val runtimes = JavaRuntimeManager(context)

    private companion object {
        /** How long to wait for the game screen to hand its surface to GLFW before giving up. */
        const val SURFACE_TIMEOUT_MS = 20_000L

        /**
         * Set when the JVM thread starts. A process can start the JVM only once, so later launches are refused.
         * It is process-wide, because each launch may come from a new GameLauncher instance.
         */
        @Volatile
        private var jvmStarted = false
    }

    /** Loads the native libraries. Throws UnsatisfiedLinkError if they are missing from the APK. */
    private fun loadNative() {
        System.loadLibrary("mojoexec")
        System.loadLibrary("mojort")
        AppLog.write("Native bridge: ${NativeBridge.nativeVersion()}")
    }

    /** The LWJGL version the game asks for, read from its metadata. Null if it is not listed. */
    private fun lwjglVersion(json: JSONObject): String? {
        val libraries = json.optJSONArray("libraries") ?: return null
        for (i in 0 until libraries.length()) {
            val name = libraries.getJSONObject(i).optString("name")
            if (name.startsWith("org.lwjgl:lwjgl:")) return name.removePrefix("org.lwjgl:lwjgl:")
        }
        return null
    }

    /** The JNA version the game asks for, read from its metadata. Null if it is not listed. */
    private fun jnaVersion(json: JSONObject): String? {
        val libraries = json.optJSONArray("libraries") ?: return null
        for (i in 0 until libraries.length()) {
            val name = libraries.getJSONObject(i).optString("name")
            if (name.startsWith("net.java.dev.jna:jna:")) return name.removePrefix("net.java.dev.jna:jna:")
        }
        return null
    }

    /** Starts the game. Returns after starting the JVM thread. Progress goes to [onStatus]. */
    fun launch(
        versionId: String,
        ramMb: Int = 2048,
        renderer: GlRenderer = GlRenderer.DEFAULT,
        onStatus: (String) -> Unit,
    ) {
        // Each step goes to the downloads screen and to the loading screen over the game surface.
        val report: (String) -> Unit = { message ->
            GameSurfaceBridge.reportStatus(message)
            onStatus(message)
        }
        // The JVM can start only once per process. Refuse before anything else changes.
        if (jvmStarted) {
            report("Launch failed: Minecraft already started in this app session. Restart Void Launcher to play again.")
            return
        }
        // Allows for coarse file timestamps when looking for this launch's crash reports and game log.
        val startedAt = System.currentTimeMillis() - 2_000L
        val metaFile = File(minecraftRoot, "versions/$versionId/$versionId.json")
        check(metaFile.isFile) { "Game files for $versionId are not downloaded." }
        val json = JSONObject(metaFile.readText())
        val metadata = GameMetadataParser.parse(metaFile.readText())

        // The selected renderer may not provide the OpenGL 3.2 core that newer Minecraft needs. Stop here, before any
        // native library loads or the JVM starts, instead of letting Minecraft fail with an OpenGL error later.
        val releaseTime = json.optString("releaseTime").ifBlank { null }
        val blockReason = Gl4esRenderer.blockReason(renderer, versionId, releaseTime)
        AppLog.write(
            "Renderer: ${renderer.label}. Minecraft release time: ${releaseTime ?: "missing"}. Gate: ${blockReason ?: "none"}",
        )
        if (blockReason != null) {
            // "Launch failed" is the prefix DownloadsViewModel uses to end the busy state.
            report("Launch failed: $blockReason")
            return
        }
        // Only one renderer can load per process. Refuse before any environment variable changes.
        Gl4esRenderer.conflictReason(renderer)?.let { conflict ->
            report("Launch failed: $conflict")
            return
        }

        val spec = JavaRuntimeSpec.forMajor(metadata.javaMajorVersion)
        check(runtimes.isInstalled(spec)) { "Java ${spec.majorVersion} is not installed yet." }
        val runtimeHome = runtimes.home(spec)
        val vmPath = runtimeHome.walkTopDown().firstOrNull { it.isFile && it.name == "libjvm.so" }
            ?: error("libjvm.so was not found in the Java runtime.")
        AppLog.write("libjvm.so: ${vmPath.absolutePath}")
        AppLog.write("LWJGL version in this game: ${lwjglVersion(json) ?: "not listed"}")
        AppLog.write("JNA version in this game: ${jnaVersion(json) ?: "not listed"}")

        gameDir.mkdirs()
        report("Checking LWJGL arm64 natives...")
        val lwjglNativesDir = Lwjgl333Natives.ensure(minecraftRoot) { AppLog.write(it) }
        val jnaNativesDir = JnaNatives.ensure(minecraftRoot, jnaVersion(json)) { AppLog.write(it) }

        val classFiles = listOf(File(minecraftRoot, metadata.client.relativePath)) +
            metadata.libraries.map { File(minecraftRoot, it.relativePath) }
        val classpath = classFiles.joinToString(":") { it.absolutePath }
        AppLog.write("Classpath entries: ${classFiles.size}")

        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val lwjglExtractDir = File(context.cacheDir, "lwjgl_native/$versionId").apply { mkdirs() }

        // Same property names as PojavLauncher (JavaRunner.getJavaArgs and GameRunner), including the
        // per-version natives directory pattern GameRunner uses for libraries not bundled in the APK.
        val libraryPathDirs = listOfNotNull(jnaNativesDir, lwjglNativesDir, nativeDir).joinToString(":") { it.absolutePath }
        val javaArgs = mutableListOf(
            "-Xms512M",
            "-Xmx${ramMb}M",
            "-Djava.home=${runtimeHome.absolutePath}",
            "-Djava.io.tmpdir=${context.cacheDir.absolutePath}",
            "-Djava.library.path=$libraryPathDirs",
            "-Djna.boot.library.path=$libraryPathDirs",
            "-Duser.home=${gameDir.absolutePath}",
            "-Duser.language=en",
            "-Duser.country=US",
            "-Dos.name=Linux",
            "-Dos.version=Android-${Build.VERSION.RELEASE}",
            "-Dpojav.path.minecraft=${minecraftRoot.absolutePath}",
            "-Dorg.lwjgl.vulkan.libname=libvulkan.so",
            "-Dorg.lwjgl.spvc.libname=spirv-cross-c-shared",
            // LWJGL's OpenGL loader asks for this name. The dlopen hook returns the GL4ES handle that
            // Gl4esRenderer.prepare loaded, so this must match PojavLauncher's GameRunner.
            "-Dorg.lwjgl.opengl.libname=libGLMojo.so",
            "-Dorg.lwjgl.sdl.libname=${File(nativeDir, "libSDL3.so").absolutePath}",
            "-Dorg.lwjgl.system.allocator=system",
            "-Dorg.lwjgl.system.SharedLibraryExtractPath=${lwjglExtractDir.absolutePath}",
        ).apply {
            if (lwjglNativesDir != null) add("-Dorg.lwjgl.librarypath=${lwjglNativesDir.absolutePath}")
        } + listOf(
            // Default process launching does not work on Android.
            "-Djdk.lang.Process.launchMechanism=FORK",
            "-Djava.class.path=$classpath",
            // If the JVM itself crashes, it writes its report here. The app copies it into the log on next start.
            "-XX:ErrorFile=${File(context.filesDir, "jvm-error-%p.log").absolutePath}",
        )
        AppLog.write(
            "JVM properties: " + javaArgs
                .filterNot { it.startsWith("-Djava.class.path") }
                .joinToString(" | ")
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

        report("Loading native libraries...")
        loadNative()
        // Installed before the renderer loads, so the renderer's own output and any native crash are captured.
        // It truncates the native output file, so it runs once per launch.
        AppLog.beginNativeOutput()
        NativeBridge.installCrashLog(AppLog.path())
        AppLog.write("Native crash catcher installed")

        // Same environment the PojavLauncher JVM expects. It is set before the renderer loads, because GL4ES
        // reads its LIBGL_* variables at load time. PojavLauncher's GameRenderer does the same order.
        val libsDir = checkNotNull(vmPath.parentFile?.parentFile)
        val ldPath = listOf(
            libsDir,
            nativeDir,
            vmPath.parentFile,
            File(libsDir, "jli"),
        ).let { base -> listOfNotNull(jnaNativesDir, lwjglNativesDir) + base }
            .joinToString(":") { it.absolutePath }
        Os.setenv("JAVA_HOME", runtimeHome.absolutePath, true)
        Os.setenv("HOME", gameDir.absolutePath, true)
        Os.setenv("TMPDIR", context.cacheDir.absolutePath, true)
        Os.setenv("LD_LIBRARY_PATH", ldPath, true)
        JREUtils.setLdLibraryPath(ldPath)
        // Clear the variables that belong to other renderers, so this launch sees only its own settings.
        val ownKeys = Gl4esRenderer.environment(renderer).keys
        for (key in Gl4esRenderer.allEnvironmentKeys() - ownKeys) Os.unsetenv(key)
        Gl4esRenderer.environment(renderer).forEach { (key, value) -> Os.setenv(key, value, true) }
        // Read back what the process will see, so the log proves the renderer settings were applied.
        for (key in Gl4esRenderer.environment(renderer).keys) {
            AppLog.write("Renderer env $key=${Os.getenv(key) ?: "(unset)"}")
        }
        AppLog.write("Environment set. LD_LIBRARY_PATH: $ldPath")

        // The renderer must be loaded before the JVM starts. Without it GLFW cannot find an OpenGL
        // EGL config and window creation fails with "EGL: Failed to find support for OpenGL".
        report("Loading the OpenGL renderer...")
        val rendererLoaded = Gl4esRenderer.prepare(nativeDir, renderer) { AppLog.write(it) }
        AppLog.importNativeOutput()
        if (!rendererLoaded) {
            AppLog.copySystemLog()
            report("Launch failed: OpenGL renderer failed to load. See the log for details.")
            return
        }

        // The GLFW bridge must be initialised before the game's first glfwInit(), which runs on the
        // JVM thread below. Its static initializer loads libglfw.so and runs the native initialize(),
        // which stores the JavaVM the render thread needs. PojavLauncher does the same in Platform.initialize.
        // The callback is called from glfwInit, and native code requires it to be non-null.
        GLFW.setInitCallback { AppLog.write("GLFW init callback received (glfwInit reached the Android bridge)") }
        // Minecraft's copy and paste goes through these. Without an implementation, GLFW's callbacks would fail.
        GLFW.setClipboardImpl(GameClipboard(context))
        AppLog.write("GLFW bridge initialised before JVM start")

        // Shows the game screen and waits for its rendering surface. The surface goes to GLFW first, and
        // only then does the JVM start, the same order as PojavLauncher (onSurfaceAvailable, then runCraft).
        GameSurfaceBridge.beginGame()
        GameSurfaceBridge.markGlfwReady()
        report("Waiting for the game screen...")
        val surfaceReady = GameSurfaceBridge.awaitFirstSurface(SURFACE_TIMEOUT_MS)
        if (GameSurfaceBridge.isLaunchCancelled()) {
            GameSurfaceBridge.endGame()
            AppLog.write("Launch cancelled from the loading screen. Minecraft was not started.")
            report("Launch cancelled.")
            return
        }
        if (!surfaceReady) {
            GameSurfaceBridge.endGame()
            AppLog.write("No rendering surface after ${SURFACE_TIMEOUT_MS / 1000} s. Minecraft was not started.")
            report("Launch failed: the game screen did not appear. Keep the app open and try again.")
            return
        }
        AppLog.write("Rendering surface ready. Starting the JVM.")

        report("Starting Java ${spec.majorVersion} and Minecraft $versionId...")
        // The native exit handler needs the JVM and its exit class before the game can abort or exit. PojavLauncher's
        // GameRunner calls this before startJvm for the same reason.
        JavaRunner.nativeSetupExit(context)
        jvmStarted = true
        Thread(null, {
            try {
                AppLog.write("JVM thread started")

                // Minecraft writes its logs to a relative folder, so start from the game folder.
                try {
                    val result = JREUtils.chdir(gameDir.absolutePath)
                    AppLog.write("Working folder set to ${gameDir.absolutePath} (result $result)")
                } catch (e: UnsatisfiedLinkError) {
                    AppLog.write("Could not set working folder: ${e.message}")
                }

                val javaArgsPtr = JavaRunner.nativeTransferArguments(javaArgs.toTypedArray())
                AppLog.write("JVM arguments transferred (${javaArgs.size} items)")
                val appArgsPtr = JavaRunner.nativeTransferArguments(gameArgs.toTypedArray())
                AppLog.write("Game arguments transferred (${gameArgs.size} items)")

                AppLog.write("Calling nativeLoadJVM for $mainClass")
                val ok = JavaRunner.nativeLoadJVM(vmPath.absolutePath, javaArgsPtr, mainClass, appArgsPtr, false)
                AppLog.write("nativeLoadJVM returned $ok")
                report(if (ok) "Minecraft exited." else "Minecraft did not start.")
            } catch (e: Throwable) {
                AppLog.write("JVM thread error: ${e.message ?: e.javaClass.simpleName}")
                report("Launch failed: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                // The game has stopped. Hide the surface, and stop forwarding to GLFW so the teardown cannot wait.
                GameSurfaceBridge.endGame()
                collectDiagnostics(startedAt)
            }
        }, "minecraft-jvm", 64L * 1024 * 1024).start()
    }

    /**
     * Copies what explains how the game stopped into the log: native output, the system log, Minecraft's own
     * log, and any crash report written since this launch started. Runs after the JVM returns.
     * A native crash that kills the process skips this; the native crash catcher and the JVM error file cover that case.
     */
    private fun collectDiagnostics(startedAt: Long) {
        AppLog.importNativeOutput()
        AppLog.copySystemLog()
        val gameLog = File(gameDir, "logs/latest.log")
        if (gameLog.isFile && gameLog.lastModified() >= startedAt) {
            AppLog.copyFileTail("Minecraft log", gameLog)
        } else {
            AppLog.write("Minecraft log: logs/latest.log was not written during this launch")
        }
        val crash = File(gameDir, "crash-reports").listFiles { f -> f.isFile && f.lastModified() >= startedAt }
            ?.maxByOrNull { it.lastModified() }
        if (crash != null) {
            AppLog.copyFileTail("Minecraft crash report", crash)
        } else {
            AppLog.write("No Minecraft crash report was written during this launch")
        }
    }

    private fun substitute(text: String, values: Map<String, String>): String {
        var result = text
        for ((key, value) in values) result = result.replace("\${$key}", value)
        return result
    }
}

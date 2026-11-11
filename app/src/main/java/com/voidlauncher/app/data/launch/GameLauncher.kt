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
        AppLog.write("libjvm.so: ${vmPath.absolutePath}")
        AppLog.write("LWJGL version in this game: ${lwjglVersion(json) ?: "not listed"}")
        AppLog.write("JNA version in this game: ${jnaVersion(json) ?: "not listed"}")

        gameDir.mkdirs()
        onStatus("Checking LWJGL arm64 natives...")
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

        onStatus("Loading native libraries...")
        loadNative()

        // The GLFW bridge must be initialised before the game's first glfwInit(), which runs on the
        // JVM thread below. Its static initializer loads libglfw.so and runs the native initialize(),
        // which stores the JavaVM the render thread needs. PojavLauncher does the same in Platform.initialize.
        // The callback is called from glfwInit, and native code requires it to be non-null.
        GLFW.setInitCallback { AppLog.write("GLFW init callback received (glfwInit reached the Android bridge)") }
        AppLog.write("GLFW bridge initialised before JVM start")

        // Same environment the PojavLauncher JVM expects.
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
        AppLog.write("Environment set. LD_LIBRARY_PATH: $ldPath")

        onStatus("Starting Java ${spec.majorVersion} and Minecraft $versionId...")
        Thread(null, {
            try {
                AppLog.write("JVM thread started")
                NativeBridge.installCrashLog(AppLog.path())
                AppLog.write("Native crash catcher installed")

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
                onStatus(if (ok) "Minecraft exited." else "Minecraft did not start.")
            } catch (e: Throwable) {
                AppLog.write("JVM thread error: ${e.message ?: e.javaClass.simpleName}")
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

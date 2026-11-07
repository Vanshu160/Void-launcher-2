#!/usr/bin/env bash
# Void Launcher 0.8.7 - add the ARM 64-bit LWJGL natives (MojoLauncher unilwjgl3-builder 3.2.3).
#  - the app downloads the four ARM jars on the first launch and adds them to the classpath
#  - the launch log shows the LWJGL version this game asks for, to compare with 3.2.3
# Run from the ROOT of your Void-launcher-2 repository.
set -e

if [ ! -d .git ] || [ ! -f app/build.gradle.kts ]; then
  echo "ERROR: run this inside the Void-launcher-2 repository folder."
  exit 1
fi

LAUNCH=app/src/main/java/com/voidlauncher/app/data/launch
mkdir -p "$LAUNCH"

# ---------------------------------------------------------------------------
cat > "$LAUNCH/ArmNatives.kt" << 'KT_EOF'
package com.voidlauncher.app.data.launch

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 64-bit ARM builds of the LWJGL native libraries, from MojoLauncher's unilwjgl3-builder release.
 * Mojang only ships x86-64 Linux natives, so these are needed for ARM phones.
 */
object ArmNatives {
    private const val BASE_URL =
        "https://github.com/MojoLauncher/unilwjgl3-builder/releases/download/v3.2.3-r6/"

    private val jars = listOf(
        "lwjgl-natives-linux-arm64-3.2.3.jar",
        "lwjgl-opengl-natives-linux-arm64-3.2.3.jar",
        "lwjgl-stb-natives-linux-arm64-3.2.3.jar",
        "lwjgl-tinyfd-natives-linux-arm64-3.2.3.jar",
    )

    /**
     * Returns the jars that are on disk, downloading any that are missing.
     * A jar that fails to download is skipped and reported through [log].
     */
    fun ensure(minecraftRoot: File, log: (String) -> Unit): List<File> {
        val dir = File(minecraftRoot, "arm-natives")
        dir.mkdirs()
        val files = mutableListOf<File>()
        for (name in jars) {
            val target = File(dir, name)
            if (!target.isFile || target.length() == 0L) {
                try {
                    download(BASE_URL + name, target)
                    log("Downloaded $name")
                } catch (e: Exception) {
                    target.delete()
                    log("Could not download $name: ${e.message ?: e.javaClass.simpleName}")
                    continue
                }
            }
            files += target
        }
        return files
    }

    private fun download(url: String, target: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            val partial = File(target.parentFile, target.name + ".part")
            connection.inputStream.use { input ->
                partial.outputStream().use { output -> input.copyTo(output) }
            }
            check(partial.renameTo(target)) { "could not save the file" }
        } finally {
            connection.disconnect()
        }
    }
}
KT_EOF

# ---------------------------------------------------------------------------
cat > "$LAUNCH/GameLauncher.kt" << 'KT_EOF'
package com.voidlauncher.app.data.launch

import android.content.Context
import android.system.Os
import com.voidlauncher.app.NativeBridge
import com.voidlauncher.app.data.log.AppLog
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

        gameDir.mkdirs()
        onStatus("Fetching ARM graphics libraries...")
        val armJars = ArmNatives.ensure(minecraftRoot) { AppLog.write(it) }
        AppLog.write("ARM graphics libraries on the classpath: ${armJars.size} of 4")

        val classFiles = listOf(File(minecraftRoot, metadata.client.relativePath)) +
            metadata.libraries.map { File(minecraftRoot, it.relativePath) } +
            armJars
        val classpath = classFiles.joinToString(":") { it.absolutePath }
        AppLog.write("Classpath entries: ${classFiles.size}")

        val javaArgs = mutableListOf(
            "-Xms512M",
            "-Xmx${ramMb}M",
            "-Djava.home=${runtimeHome.absolutePath}",
            "-Djava.io.tmpdir=${context.cacheDir.absolutePath}",
            "-Duser.home=${gameDir.absolutePath}",
            "-Dpojav.path.minecraft=${minecraftRoot.absolutePath}",
            "-Dorg.lwjgl.system.allocator=system",
            "-Djava.class.path=$classpath",
            // If the JVM itself crashes, it writes its report here. The app copies it into the log on next start.
            "-XX:ErrorFile=${File(context.filesDir, "jvm-error-%p.log").absolutePath}",
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
KT_EOF

# ---------------------------------------------------------------------------
echo
echo "Done. Files written for milestone 0.8.7 (ARM LWJGL natives)."
echo "Next:  git add . && git commit -m \"Milestone 0.8.7: ARM LWJGL natives and version check\" && git push"

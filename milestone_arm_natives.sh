#!/usr/bin/env bash
# Void Launcher 0.8.6 - ARM 64-bit graphics libraries, game working folder, full JVM output in the log.
#  - metadata parser accepts library rules for linux/arm64 (LWJGL natives for this phone)
#  - the JVM starts from the game folder, so Minecraft can create its logs folder
#  - the log keeps the END of the JVM output, which is where the errors are
# Run from the ROOT of your Void-launcher-2 repository.
# After applying: open Downloads and tap "Download game files" once, so the ARM libraries are fetched.
set -e

if [ ! -d .git ] || [ ! -f app/build.gradle.kts ]; then
  echo "ERROR: run this inside the Void-launcher-2 repository folder."
  exit 1
fi

BASE=app/src/main/java/com/voidlauncher/app
mkdir -p "$BASE/data/remote" "$BASE/data/launch" "$BASE/data/log"

# ---------------------------------------------------------------------------
cat > "$BASE/data/remote/GameMetadataParser.kt" << 'KT_EOF'
package com.voidlauncher.app.data.remote

import com.voidlauncher.app.domain.model.AssetObject
import com.voidlauncher.app.domain.model.GameFile
import com.voidlauncher.app.domain.model.GameMetadata
import org.json.JSONArray
import org.json.JSONObject

object GameMetadataParser {

    /** The CPU this launcher runs on. Only 64-bit ARM phones are supported for now. */
    private const val DEVICE_ARCH = "arm64"

    /** Parses a version metadata JSON. Throws if the JSON is missing required fields. */
    fun parse(json: String): GameMetadata {
        val root = JSONObject(json)
        val id = root.getString("id")

        val clientJson = root.getJSONObject("downloads").getJSONObject("client")
        val client = GameFile(
            relativePath = "versions/$id/$id.jar",
            url = clientJson.getString("url"),
            sha1 = clientJson.optString("sha1", "").takeIf { it.isNotEmpty() },
        )

        val assetRef = root.getJSONObject("assetIndex")
        val assetIndexId = assetRef.getString("id")
        val assetIndex = GameFile(
            relativePath = "assets/indexes/$assetIndexId.json",
            url = assetRef.getString("url"),
            sha1 = assetRef.optString("sha1", "").takeIf { it.isNotEmpty() },
        )

        val javaMajor = root.optJSONObject("javaVersion")?.optInt("majorVersion", 8) ?: 8

        return GameMetadata(
            id = id,
            client = client,
            libraries = parseLibraries(root.getJSONArray("libraries")),
            assetIndex = assetIndex,
            assetIndexId = assetIndexId,
            javaMajorVersion = javaMajor,
        )
    }

    /** Reads the asset index JSON. Duplicate hashes are returned as-is; callers remove them. */
    fun parseAssetIndex(json: String): List<AssetObject> {
        val objects = JSONObject(json).getJSONObject("objects")
        val names = objects.keys()
        return buildList {
            while (names.hasNext()) {
                val entry = objects.getJSONObject(names.next())
                add(AssetObject(hash = entry.getString("hash"), size = entry.optLong("size")))
            }
        }
    }

    private fun parseLibraries(entries: JSONArray): List<GameFile> {
        val files = mutableListOf<GameFile>()
        for (i in 0 until entries.length()) {
            val library = entries.getJSONObject(i)
            if (!isAllowed(library.optJSONArray("rules"))) continue
            val downloads = library.optJSONObject("downloads") ?: continue

            downloads.optJSONObject("artifact")?.let { files += it.toLibraryFile() }

            // Older versions list native libraries as classifiers, one per OS.
            val classifier = library.optJSONObject("natives")?.optString("linux").orEmpty()
            if (classifier.isNotEmpty()) {
                downloads.optJSONObject("classifiers")?.optJSONObject(classifier)
                    ?.let { files += it.toLibraryFile() }
            }
        }
        return files
    }

    private fun JSONObject.toLibraryFile(): GameFile = GameFile(
        relativePath = "libraries/" + getString("path"),
        url = getString("url"),
        sha1 = optString("sha1", "").takeIf { it.isNotEmpty() },
    )

    /**
     * Mojang rules: the last rule that applies decides. A rule without an OS applies to every OS.
     * Rules gated by features (demo mode, custom resolution) never apply to a launcher install.
     * Android is treated as "linux", and the CPU must match [DEVICE_ARCH] when a rule names one.
     */
    private fun isAllowed(rules: JSONArray?): Boolean {
        if (rules == null || rules.length() == 0) return true
        var allowed = false
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            if (rule.has("features")) continue
            val os = rule.optJSONObject("os")
            val applies = when {
                os == null -> true
                else -> {
                    val nameMatches = !os.has("name") || os.optString("name") == "linux"
                    val archMatches = !os.has("arch") || os.optString("arch") == DEVICE_ARCH
                    nameMatches && archMatches
                }
            }
            if (applies) allowed = rule.getString("action") == "allow"
        }
        return allowed
    }
}
KT_EOF

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
 * A small log kept in the app's private storage. It records launch steps, Java crashes,
 * native crashes, JVM crash reports and JVM console output, so the text can be copied from
 * the Downloads screen.
 */
object AppLog {
    private const val MAX_BYTES = 200_000
    private const val IMPORT_LIMIT = 60_000
    private const val JVM_OUTPUT_NAME = "void-log.txt.jvm-out"
    private var file: File? = null
    private val time = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun init(context: Context) {
        val target = File(context.filesDir, "void-log.txt")
        file = target
        importJvmErrorFiles(context.filesDir)
        importJvmOutput(context.filesDir)
        installCrashHandler()
    }

    /** Absolute path of the log file, used by the native code. Empty before init. */
    fun path(): String = file?.absolutePath ?: ""

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

    /** Copies JVM crash reports (jvm-error-<pid>.log) from an earlier run into the log, then removes them. */
    private fun importJvmErrorFiles(dir: File) {
        val target = file ?: return
        dir.listFiles { f -> f.isFile && f.name.startsWith("jvm-error-") }?.forEach { report ->
            try {
                val text = report.readText().takeLast(IMPORT_LIMIT)
                target.appendText("--- JVM crash report ${report.name} (from an earlier run) ---\n$text\n")
                report.delete()
            } catch (e: Exception) {
                // Ignore unreadable reports.
            }
        }
    }

    /** Copies the JVM's console output from the last launch into the log, then removes it. */
    private fun importJvmOutput(dir: File) {
        val target = file ?: return
        val output = File(dir, JVM_OUTPUT_NAME)
        if (!output.isFile) return
        try {
            // The end of the output is kept: that is where the errors are.
            val text = output.readText().takeLast(IMPORT_LIMIT)
            if (text.isNotBlank()) {
                target.appendText("--- JVM console output (from the last launch, last part) ---\n$text\n")
            }
            output.delete()
        } catch (e: Exception) {
            // Ignore unreadable output.
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
cat > "$BASE/data/launch/GameLauncher.kt" << 'KT_EOF'
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

        gameDir.mkdirs()
        val classpath = (listOf(File(minecraftRoot, metadata.client.relativePath)) +
            metadata.libraries.map { File(minecraftRoot, it.relativePath) })
            .joinToString(":") { it.absolutePath }
        AppLog.write("Classpath entries: ${classpath.split(":").size}")

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
echo "Done. Files written for milestone 0.8.6 (ARM graphics libraries and working folder)."
echo "Next:  git add . && git commit -m \"Milestone 0.8.6: arm64 libraries, game working folder, full JVM output\" && git push"
echo "After installing: open Downloads and tap \"Download game files\" once, then Start game once."

#!/usr/bin/env bash
# Void Launcher 0.8.2 - find where the game crashes.
#  - step-by-step log lines written from the JVM thread
#  - native crash catcher: signal, fault address, call stack and memory map go to the launch log
#  - JVM crash report (hs_err) is copied into the launch log on the next app start
# Run from the ROOT of your Void-launcher-2 repository.
set -e

if [ ! -d .git ] || [ ! -f app/build.gradle.kts ]; then
  echo "ERROR: run this inside the Void-launcher-2 repository folder."
  exit 1
fi

BASE=app/src/main/java/com/voidlauncher/app
LAUNCH=$BASE/data/launch
LOG=$BASE/data/log
mkdir -p "$LAUNCH" "$LOG" app/src/main/cpp

# ---------------------------------------------------------------------------
cat > "$LOG/AppLog.kt" << 'KT_EOF'
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
 * native crashes and JVM crash reports, so the text can be copied from the Downloads screen.
 */
object AppLog {
    private const val MAX_BYTES = 200_000
    private var file: File? = null
    private val time = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun init(context: Context) {
        val target = File(context.filesDir, "void-log.txt")
        file = target
        importJvmErrorFiles(context.filesDir)
        installCrashHandler()
    }

    /** Absolute path of the log file, used by the native crash catcher. Empty before init. */
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
                val text = report.readText().take(60_000)
                target.appendText("--- JVM crash report ${report.name} (from an earlier run) ---\n$text\n")
                report.delete()
            } catch (e: Exception) {
                // Ignore unreadable reports.
            }
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
cat > "$BASE/NativeBridge.kt" << 'KT_EOF'
package com.voidlauncher.app

/**
 * Entry points of voidnative (app/src/main/cpp/voidnative.c).
 * The JNI names must match this package and class name.
 */
object NativeBridge {
    init {
        System.loadLibrary("voidnative")
    }

    @JvmStatic
    external fun nativeVersion(): String

    /** Writes signal, fault address, call stack and memory map to [path] if the process crashes natively. */
    @JvmStatic
    external fun installCrashLog(path: String)
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
cat > app/src/main/cpp/voidnative.c << 'C_EOF'
#include <jni.h>
#include <fcntl.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <unwind.h>

// Native crash catcher. When the process gets a fatal signal, the signal, fault address,
// call stack and memory map are appended to the launch log, then the default crash runs.
// The memory map lets us match the addresses to library names later.

static char crash_log_path[512];

static void write_text(int fd, const char *text) {
    size_t len = strlen(text);
    (void) write(fd, text, len);
}

static void write_hex(int fd, uintptr_t value) {
    char digits[32];
    int count = 0;
    do {
        digits[count++] = "0123456789abcdef"[value & 0xf];
        value >>= 4;
    } while (value != 0 && count < 31);
    char out[40];
    int pos = 0;
    out[pos++] = '0';
    out[pos++] = 'x';
    while (count > 0) {
        out[pos++] = digits[--count];
    }
    out[pos] = '\0';
    write_text(fd, out);
}

struct backtrace_state {
    int fd;
    int frames;
};

static _Unwind_Reason_Code collect_frame(struct _Unwind_Context *context, void *arg) {
    struct backtrace_state *state = (struct backtrace_state *) arg;
    uintptr_t pc = _Unwind_GetIP(context);
    if (pc == 0 || state->frames >= 80) {
        return _URC_END_OF_STACK;
    }
    write_text(state->fd, "  pc ");
    write_hex(state->fd, pc);
    write_text(state->fd, "\n");
    state->frames++;
    return _URC_NO_REASON;
}

static void copy_memory_map(int fd) {
    int in = open("/proc/self/maps", O_RDONLY);
    if (in < 0) {
        write_text(fd, "(memory map unavailable)\n");
        return;
    }
    char buffer[1024];
    ssize_t n;
    while ((n = read(in, buffer, sizeof buffer)) > 0) {
        (void) write(fd, buffer, (size_t) n);
    }
    close(in);
}

static void crash_handler(int signal_number, siginfo_t *info, void *context) {
    (void) context;
    int fd = open(crash_log_path, O_WRONLY | O_CREAT | O_APPEND, 0644);
    if (fd >= 0) {
        write_text(fd, "\n=== NATIVE CRASH ===\nsignal ");
        write_hex(fd, (uintptr_t) signal_number);
        write_text(fd, "  fault address ");
        write_hex(fd, info != NULL ? (uintptr_t) info->si_addr : 0);
        write_text(fd, "\ncall stack:\n");

        struct backtrace_state state = { fd, 0 };
        _Unwind_Backtrace(collect_frame, &state);

        write_text(fd, "--- memory map ---\n");
        copy_memory_map(fd);
        write_text(fd, "=== END NATIVE CRASH ===\n");
        close(fd);
    }
    // Let the system handle the crash normally afterwards.
    signal(signal_number, SIG_DFL);
    raise(signal_number);
}

JNIEXPORT jstring JNICALL
Java_com_voidlauncher_app_NativeBridge_nativeVersion(JNIEnv *env, jclass clazz) {
    (void) clazz;
    return (*env)->NewStringUTF(env, "voidnative 0.8.2");
}

JNIEXPORT void JNICALL
Java_com_voidlauncher_app_NativeBridge_installCrashLog(JNIEnv *env, jclass clazz, jstring path) {
    (void) clazz;
    const char *text = (*env)->GetStringUTFChars(env, path, NULL);
    if (text == NULL) {
        return;
    }
    snprintf(crash_log_path, sizeof crash_log_path, "%s", text);
    (*env)->ReleaseStringUTFChars(env, path, text);

    struct sigaction action;
    memset(&action, 0, sizeof action);
    action.sa_sigaction = crash_handler;
    action.sa_flags = SA_SIGINFO;
    sigemptyset(&action.sa_mask);

    sigaction(SIGSEGV, &action, NULL);
    sigaction(SIGABRT, &action, NULL);
    sigaction(SIGBUS, &action, NULL);
    sigaction(SIGILL, &action, NULL);
    sigaction(SIGFPE, &action, NULL);
}
C_EOF

# ---------------------------------------------------------------------------
echo
echo "Done. Files written for milestone 0.8.2 (find the native crash)."
echo "Next:  git add . && git commit -m \"Milestone 0.8.2: native crash catcher and JVM crash reports\" && git push"

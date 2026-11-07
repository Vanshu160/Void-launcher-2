#!/usr/bin/env bash
# Void Launcher 0.8.3 - capture the JVM's own error messages in the launch log.
# The JVM writes its errors to stdout/stderr, which Android sends to the system log, not to the app.
# This redirects them into a file that the app copies into the launch log on the next start.
# Run from the ROOT of your Void-launcher-2 repository.
set -e

if [ ! -d .git ] || [ ! -f app/build.gradle.kts ]; then
  echo "ERROR: run this inside the Void-launcher-2 repository folder."
  exit 1
fi

BASE=app/src/main/java/com/voidlauncher/app
mkdir -p "$BASE/data/log" app/src/main/cpp

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
                val text = report.readText().take(60_000)
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
            val text = output.readText().take(60_000)
            if (text.isNotBlank()) {
                target.appendText("--- JVM console output (from the last launch) ---\n$text\n")
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
cat > app/src/main/cpp/voidnative.c << 'C_EOF'
#include <jni.h>
#include <fcntl.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <unwind.h>

// Native crash catcher and JVM console capture.
// When the process gets a fatal signal, the signal, fault address, call stack and memory map
// are appended to the launch log, then the default crash runs.
// The JVM's stdout and stderr are redirected to a file next to the log, so its error messages
// reach the app instead of only Android's system log.

static char crash_log_path[512];
static char jvm_output_path[600];

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
    return (*env)->NewStringUTF(env, "voidnative 0.8.3");
}

JNIEXPORT void JNICALL
Java_com_voidlauncher_app_NativeBridge_installCrashLog(JNIEnv *env, jclass clazz, jstring path) {
    (void) clazz;
    const char *text = (*env)->GetStringUTFChars(env, path, NULL);
    if (text == NULL) {
        return;
    }
    snprintf(crash_log_path, sizeof crash_log_path, "%s", text);
    snprintf(jvm_output_path, sizeof jvm_output_path, "%s.jvm-out", text);
    (*env)->ReleaseStringUTFChars(env, path, text);

    // Send the JVM's stdout and stderr to a fresh file for this launch.
    int output = open(jvm_output_path, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (output >= 0) {
        dup2(output, STDOUT_FILENO);
        dup2(output, STDERR_FILENO);
        close(output);
    }

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
echo "Done. Files written for milestone 0.8.3 (JVM console capture)."
echo "Next:  git add . && git commit -m \"Milestone 0.8.3: capture JVM console output\" && git push"

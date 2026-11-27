package com.voidlauncher.app.data.log

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * A small log kept in the app's private storage. It records launch steps, Java crashes,
 * native crashes, JVM crash reports and JVM console output, so the text can be copied from
 * the Downloads screen.
 */
object AppLog {
    private const val MAX_BYTES = 200_000
    private const val HEAD_CHARS = 8_000
    private const val TAIL_CHARS = 40_000
    private const val JVM_OUTPUT_NAME = "void-log.txt.jvm-out"
    private var file: File? = null
    private var outputFile: File? = null
    private var nativeOutputOffset = 0L
    private val time = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun init(context: Context) {
        val target = File(context.filesDir, "void-log.txt")
        file = target
        outputFile = File(context.filesDir, JVM_OUTPUT_NAME)
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

    /** Keeps the start and the end of a long text. The start holds the crash reason. */
    private fun headAndTail(text: String): String {
        if (text.length <= HEAD_CHARS + TAIL_CHARS) return text
        return text.take(HEAD_CHARS) +
            "\n... (${text.length - HEAD_CHARS - TAIL_CHARS} characters left out) ...\n" +
            text.takeLast(TAIL_CHARS)
    }

    /** Copies JVM crash reports (jvm-error-<pid>.log) from an earlier run into the log, then removes them. */
    private fun importJvmErrorFiles(dir: File) {
        val target = file ?: return
        dir.listFiles { f -> f.isFile && f.name.startsWith("jvm-error-") }?.forEach { report ->
            try {
                val text = headAndTail(report.readText())
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
            val text = headAndTail(output.readText())
            if (text.isNotBlank()) {
                target.appendText("--- JVM console output (from the last launch) ---\n$text\n")
            }
            output.delete()
        } catch (e: Exception) {
            // Ignore unreadable output.
        }
    }

    /**
     * Copies the native output (stdout and stderr) written since the last call into the log. The renderer's own
     * printf messages and the JVM's console both go there. Safe to call at any time; each call copies only new text.
     */
    @Synchronized
    fun importNativeOutput() {
        val output = outputFile ?: return
        try {
            if (!output.isFile) return
            val length = output.length()
            // The native side truncates the file when a launch starts, so a shorter file means a new launch.
            if (length < nativeOutputOffset) nativeOutputOffset = 0L
            if (length == nativeOutputOffset) return
            val bytes = RandomAccessFile(output, "r").use { raf ->
                raf.seek(nativeOutputOffset)
                ByteArray((length - nativeOutputOffset).toInt()).also { raf.readFully(it) }
            }
            nativeOutputOffset = length
            val text = String(bytes, Charsets.UTF_8).trimEnd()
            if (text.isNotBlank()) write("Native output (stdout and stderr):\n${headAndTail(text)}")
        } catch (e: Exception) {
            // Ignore unreadable output.
        }
    }

    /**
     * Called once per launch, right after the native crash catcher truncates the native output file. Resets the
     * read position, so the new launch's output is copied from its start.
     */
    @Synchronized
    fun beginNativeOutput() {
        nativeOutputOffset = 0L
    }

    /**
     * Copies this process's own Android system log (logcat) into the log. Renderer messages sent to the system
     * log, and LWJGL's loader messages, appear there and nowhere else.
     */
    fun copySystemLog() {
        try {
            val pid = android.os.Process.myPid()
            val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "--pid=$pid")
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor(10, TimeUnit.SECONDS)
            if (text.isNotBlank()) {
                write("System log for this process (logcat):\n${headAndTail(text.trimEnd())}")
            } else {
                write("System log for this process: no lines were returned")
            }
        } catch (e: Exception) {
            write("System log unavailable: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Copies the last [lines] lines of a text file, such as the game's own log, into the log under [title]. */
    fun copyFileTail(title: String, source: File, lines: Int = 200) {
        try {
            if (!source.isFile) {
                write("$title: ${source.name} was not written")
                return
            }
            val tail = source.readLines().takeLast(lines).joinToString("\n")
            write("$title (${source.name}, last $lines lines):\n${headAndTail(tail)}")
        } catch (e: Exception) {
            write("$title: could not read ${source.name} (${e.message ?: e.javaClass.simpleName})")
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

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

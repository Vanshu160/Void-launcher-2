package net.kdt.pojavlaunch

import android.content.Context
import android.os.Process
import com.voidlauncher.app.data.log.AppLog

/**
 * Receives the Minecraft JVM's exit request. The native exit thread (libmojort.so, abort_wait.c) calls
 * [showExitMessage] by this exact class name and signature, so the name must not change.
 *
 * Void Launcher does not show PojavLauncher's exit dialog. It records the exit in the app log and ends the
 * process. A JVM cannot be started again in the same process, so the app has to restart before another game.
 */
class ExitActivity private constructor() {
    companion object {
        @JvmStatic
        fun showExitMessage(context: Context, code: Int, isSignal: Boolean) {
            val cause = if (isSignal) "a fatal signal" else "System.exit"
            AppLog.write("Minecraft JVM exit: code $code, caused by $cause. Closing the app process.")
            Process.killProcess(Process.myPid())
        }
    }
}

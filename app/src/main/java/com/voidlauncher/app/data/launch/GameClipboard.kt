package com.voidlauncher.app.data.launch

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.voidlauncher.app.data.log.AppLog
import git.artdeell.dnbootstrap.glfw.GLFWClipboard

/**
 * The system clipboard for Minecraft's copy and paste (GLFW clipboard callbacks).
 *
 * Android 10 and later return no clip to apps that are not in the foreground. Reading then returns an empty
 * string, which GLFW accepts. Any failure is logged and treated the same way, so a clipboard problem never
 * reaches the native callback as an exception.
 *
 * Threading: GLFW calls these from the JVM thread. The clipboard service accepts calls from any thread.
 */
class GameClipboard(context: Context) : GLFWClipboard {
    private val appContext = context.applicationContext
    private val clipboard = appContext.getSystemService(ClipboardManager::class.java)

    override fun getClipboardString(): String {
        return try {
            val clip = clipboard?.primaryClip ?: return ""
            if (clip.itemCount == 0) return ""
            clip.getItemAt(0).coerceToText(appContext)?.toString() ?: ""
        } catch (e: Exception) {
            AppLog.write("Clipboard read failed: ${e.message ?: e.javaClass.simpleName}")
            ""
        }
    }

    override fun setClipboardString(str: String) {
        try {
            clipboard?.setPrimaryClip(ClipData.newPlainText("Minecraft", str))
        } catch (e: Exception) {
            AppLog.write("Clipboard write failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}

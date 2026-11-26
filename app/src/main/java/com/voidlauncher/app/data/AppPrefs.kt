package com.voidlauncher.app.data

import android.content.Context
import android.content.SharedPreferences
import com.voidlauncher.app.data.launch.GameAction

/**
 * Launcher preferences in one SharedPreferences file. Call [init] once from the application class.
 *
 * Every read falls back to its default when storage is not ready or a stored value has the wrong type.
 * Writes are applied asynchronously, so they never block the main thread.
 */
object AppPrefs {
    private const val FILE = "void_launcher_prefs"
    private const val KEY_SELECTED_VERSION = "selectedVersion"
    private const val KEY_MOD_LOADER = "modLoader"
    private const val KEY_BIND_PREFIX = "bind."
    private const val DEFAULT_MOD_LOADER = "fabric"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
    }

    private fun <T> read(default: T, block: (SharedPreferences) -> T): T {
        val current = prefs ?: return default
        return try {
            block(current)
        } catch (e: ClassCastException) {
            default
        }
    }

    /** Applies a batch of writes. Does nothing if storage is not ready. */
    fun edit(block: (SharedPreferences.Editor) -> Unit) {
        val current = prefs ?: return
        val editor = current.edit()
        block(editor)
        editor.apply()
    }

    fun float(key: String, default: Float): Float = read(default) { it.getFloat(key, default) }

    fun bool(key: String, default: Boolean): Boolean = read(default) { it.getBoolean(key, default) }

    /** The version the user picked, or null if none was saved. */
    fun selectedVersion(): String? = read<String?>(null) { it.getString(KEY_SELECTED_VERSION, null) }

    fun saveSelectedVersion(id: String?) {
        edit { editor ->
            if (id == null) editor.remove(KEY_SELECTED_VERSION) else editor.putString(KEY_SELECTED_VERSION, id)
        }
    }

    /** The mod loader shown in the Mods screen. Unknown saved values fall back to Fabric. */
    fun modLoader(): String {
        val saved = read<String?>(null) { it.getString(KEY_MOD_LOADER, null) }
        return if (saved != null && saved in com.voidlauncher.app.data.ModRepository.LOADERS) saved else DEFAULT_MOD_LOADER
    }

    fun saveModLoader(loader: String) {
        edit { it.putString(KEY_MOD_LOADER, loader) }
    }

    /** The Android key code bound to each action. Missing entries use the action's default key. */
    fun keybinds(): Map<GameAction, Int> = GameAction.entries.associateWith { action ->
        read(action.defaultKeyCode) { it.getInt(KEY_BIND_PREFIX + action.name, action.defaultKeyCode) }
    }

    fun saveKeybinds(bindings: Map<GameAction, Int>) {
        edit { editor ->
            for (action in GameAction.entries) {
                editor.putInt(KEY_BIND_PREFIX + action.name, bindings[action] ?: action.defaultKeyCode)
            }
        }
    }
}

package com.voidlauncher.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Minecraft version the user picked. Home, Versions, Downloads and Mods all read it.
 * It is saved through [AppPrefs] and loaded by [restore] at startup.
 */
object SelectedVersion {
    private val _id = MutableStateFlow<String?>(null)
    val flow: StateFlow<String?> = _id.asStateFlow()

    /** Loads the saved choice. Call once at startup, after [AppPrefs.init]. */
    fun restore() {
        _id.value = AppPrefs.selectedVersion()
    }

    fun set(id: String?) {
        _id.value = id
        AppPrefs.saveSelectedVersion(id)
    }
}

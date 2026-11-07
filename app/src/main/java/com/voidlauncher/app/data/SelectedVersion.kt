package com.voidlauncher.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Minecraft version the user picked. Home, Versions and Mods all read it.
 * Kept in memory for now; it will be saved with settings in a later milestone.
 */
object SelectedVersion {
    private val _id = MutableStateFlow<String?>(null)
    val flow: StateFlow<String?> = _id.asStateFlow()

    fun set(id: String?) {
        _id.value = id
    }
}

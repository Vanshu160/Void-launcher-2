package com.voidlauncher.app.data.local

import android.content.Context
import java.io.File
import java.io.IOException

/** Keeps the last downloaded manifest on the phone, so the list works offline. */
class VersionCache(context: Context) {

    private val file = File(context.filesDir, "version_manifest_v2.json")

    fun read(): String? = try {
        if (file.exists()) file.readText() else null
    } catch (e: IOException) {
        null
    }

    fun write(json: String) {
        try {
            file.writeText(json)
        } catch (e: IOException) {
            // Caching is best-effort; the list still shows from memory.
        }
    }
}

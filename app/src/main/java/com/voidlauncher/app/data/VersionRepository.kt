package com.voidlauncher.app.data

import com.voidlauncher.app.data.local.VersionCache
import com.voidlauncher.app.data.remote.MojangVersionApi
import com.voidlauncher.app.data.remote.VersionManifestParser
import com.voidlauncher.app.domain.model.MinecraftVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VersionRepository(private val cache: VersionCache) {

    data class LoadResult(val versions: List<MinecraftVersion>, val fromCache: Boolean)

    /** Returns the saved list, or null if none has been downloaded yet. */
    suspend fun loadCached(): List<MinecraftVersion>? = withContext(Dispatchers.IO) {
        cache.read()?.let { parseOrNull(it) }
    }

    /**
     * Downloads the newest list and saves it. If the download fails, falls back to the
     * saved list. Throws only when there is no saved list and no network.
     */
    suspend fun refresh(): LoadResult = withContext(Dispatchers.IO) {
        try {
            val json = MojangVersionApi.fetchManifestJson()
            val versions = VersionManifestParser.parse(json)
            cache.write(json)
            LoadResult(versions, fromCache = false)
        } catch (e: Exception) {
            val saved = cache.read()?.let { parseOrNull(it) }
            if (saved != null) LoadResult(saved, fromCache = true) else throw e
        }
    }

    private fun parseOrNull(json: String): List<MinecraftVersion>? = try {
        VersionManifestParser.parse(json)
    } catch (e: Exception) {
        null
    }
}

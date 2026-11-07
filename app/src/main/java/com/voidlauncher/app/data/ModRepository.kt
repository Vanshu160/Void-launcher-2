package com.voidlauncher.app.data

import com.voidlauncher.app.data.remote.Http
import com.voidlauncher.app.data.remote.ModrinthApi
import com.voidlauncher.app.domain.model.InstalledMod
import com.voidlauncher.app.domain.model.ModSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Mods are stored per Minecraft version, in minecraft/versions/<version>/mods.
 */
class ModRepository(private val minecraftRoot: File) {

    companion object {
        const val LOADER = "fabric"
    }

    private fun modsFolder(gameVersion: String): File =
        File(minecraftRoot, "versions/$gameVersion/mods")

    suspend fun search(query: String, gameVersion: String): List<ModSearchResult> =
        withContext(Dispatchers.IO) {
            ModrinthApi.search(query, gameVersion, LOADER)
        }

    /** Installs a mod plus its required dependencies (one level deep). Returns the file names saved. */
    suspend fun install(projectId: String, gameVersion: String): List<String> =
        withContext(Dispatchers.IO) {
            val folder = modsFolder(gameVersion)
            val version = ModrinthApi.versionsFor(projectId, gameVersion, LOADER).firstOrNull()
                ?: throw IOException("No file available for Minecraft $gameVersion with Fabric")

            val saved = mutableListOf<String>()
            Http.download(version.fileUrl, File(folder, version.fileName), version.sha1)
            saved.add(version.fileName)

            for (dependency in version.dependencies.filter { it.required }) {
                val depVersion = ModrinthApi.versionsFor(dependency.projectId, gameVersion, LOADER)
                    .firstOrNull() ?: continue
                val target = File(folder, depVersion.fileName)
                if (!target.exists()) {
                    Http.download(depVersion.fileUrl, target, depVersion.sha1)
                    saved.add(depVersion.fileName)
                }
            }
            saved
        }

    suspend fun installed(gameVersion: String): List<InstalledMod> =
        withContext(Dispatchers.IO) {
            modsFolder(gameVersion)
                .listFiles { file -> file.isFile && file.extension == "jar" }
                ?.sortedBy { it.name }
                ?.map { InstalledMod(fileName = it.name, sizeKb = it.length() / 1024) }
                ?: emptyList()
        }

    suspend fun remove(gameVersion: String, fileName: String) {
        withContext(Dispatchers.IO) {
            File(modsFolder(gameVersion), fileName).delete()
        }
    }
}

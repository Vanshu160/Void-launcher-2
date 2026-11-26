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
 * What an install saved. [skippedDependencies] counts required dependencies that have no file
 * for this Minecraft version and loader, so they were not installed.
 */
data class InstallResult(val savedFiles: List<String>, val skippedDependencies: Int)

/**
 * Mods are stored per Minecraft version, in minecraft/versions/<version>/mods.
 */
class ModRepository(private val minecraftRoot: File) {

    companion object {
        /** Loaders offered in the Mods screen. Modrinth uses these names as filters. */
        val LOADERS: List<String> = listOf("fabric", "quilt", "forge", "neoforge")
    }

    private fun modsFolder(gameVersion: String): File =
        File(minecraftRoot, "versions/$gameVersion/mods")

    suspend fun search(query: String, gameVersion: String, loader: String): List<ModSearchResult> =
        withContext(Dispatchers.IO) {
            ModrinthApi.search(query, gameVersion, loader)
        }

    /** Installs a mod plus its required dependencies (one level deep). */
    suspend fun install(projectId: String, gameVersion: String, loader: String): InstallResult =
        withContext(Dispatchers.IO) {
            val folder = modsFolder(gameVersion).apply { mkdirs() }
            val version = ModrinthApi.versionsFor(projectId, gameVersion, loader).firstOrNull()
                ?: throw IOException("No file for Minecraft $gameVersion with ${loader.replaceFirstChar { it.uppercase() }}.")

            val saved = mutableListOf<String>()
            Http.download(version.fileUrl, File(folder, version.fileName), version.sha1)
            saved.add(version.fileName)

            var skipped = 0
            for (dependency in version.dependencies.filter { it.required }) {
                val depVersion = ModrinthApi.versionsFor(dependency.projectId, gameVersion, loader).firstOrNull()
                if (depVersion == null) {
                    skipped++
                    continue
                }
                val target = File(folder, depVersion.fileName)
                if (!target.exists()) {
                    Http.download(depVersion.fileUrl, target, depVersion.sha1)
                    saved.add(depVersion.fileName)
                }
            }
            InstallResult(savedFiles = saved, skippedDependencies = skipped)
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

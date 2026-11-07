package com.voidlauncher.app.data.launch

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 64-bit ARM builds of the LWJGL native libraries, from MojoLauncher's unilwjgl3-builder release.
 * Mojang only ships x86-64 Linux natives, so these are needed for ARM phones.
 */
object ArmNatives {
    private const val BASE_URL =
        "https://github.com/MojoLauncher/unilwjgl3-builder/releases/download/v3.2.3-r6/"

    private val jars = listOf(
        "lwjgl-natives-linux-arm64-3.2.3.jar",
        "lwjgl-opengl-natives-linux-arm64-3.2.3.jar",
        "lwjgl-stb-natives-linux-arm64-3.2.3.jar",
        "lwjgl-tinyfd-natives-linux-arm64-3.2.3.jar",
    )

    /**
     * Returns the jars that are on disk, downloading any that are missing.
     * A jar that fails to download is skipped and reported through [log].
     */
    fun ensure(minecraftRoot: File, log: (String) -> Unit): List<File> {
        val dir = File(minecraftRoot, "arm-natives")
        dir.mkdirs()
        val files = mutableListOf<File>()
        for (name in jars) {
            val target = File(dir, name)
            if (!target.isFile || target.length() == 0L) {
                try {
                    download(BASE_URL + name, target)
                    log("Downloaded $name")
                } catch (e: Exception) {
                    target.delete()
                    log("Could not download $name: ${e.message ?: e.javaClass.simpleName}")
                    continue
                }
            }
            files += target
        }
        return files
    }

    private fun download(url: String, target: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            val partial = File(target.parentFile, target.name + ".part")
            connection.inputStream.use { input ->
                partial.outputStream().use { output -> input.copyTo(output) }
            }
            check(partial.renameTo(target)) { "could not save the file" }
        } finally {
            connection.disconnect()
        }
    }
}

package com.voidlauncher.app.data.launch

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Real LWJGL 3.3.3 native libraries for arm64 Android, built in this repo's own CI
 * (.github/workflows/build-lwjgl-arm64.yml) from PojavLauncherTeam's own Android build of LWJGL
 * (github.com/PojavLauncherTeam/lwjgl3, wip/rebase_3.3.3). Downloaded once per device, into a
 * per-version natives folder, the same pattern PojavLauncher's own GameRunner uses.
 *
 * Until that workflow has been run at least once, this release has no assets and [ensure]
 * returns an empty list - the caller must treat that as "not built yet", not as an error to hide.
 */
object Lwjgl333Natives {
    private const val RELEASE_API =
        "https://api.github.com/repos/Vanshu160/Void-launcher-2/releases/tags/lwjgl-3.3.3-arm64"

    /**
     * Downloads any missing .so files for this release into [minecraftRoot]/natives/lwjgl-3.3.3-arm64,
     * and returns the directory. Returns null if the release does not exist yet or has no assets -
     * the LWJGL build has not been run (or has not succeeded) yet.
     */
    fun ensure(minecraftRoot: File, log: (String) -> Unit): File? {
        val dir = File(minecraftRoot, "natives/lwjgl-3.3.3-arm64")
        val assets = try {
            fetchAssetUrls()
        } catch (e: Exception) {
            log("Could not check for LWJGL arm64 natives: ${e.message ?: e.javaClass.simpleName}")
            return if (dir.listFiles()?.isNotEmpty() == true) dir else null
        }
        if (assets.isEmpty()) {
            log("LWJGL 3.3.3 arm64 natives are not built yet (no release assets found).")
            return if (dir.listFiles()?.isNotEmpty() == true) dir else null
        }
        dir.mkdirs()
        var downloaded = 0
        for ((name, url) in assets) {
            val target = File(dir, name)
            if (target.isFile && target.length() > 0) continue
            try {
                download(url, target)
                downloaded++
            } catch (e: Exception) {
                target.delete()
                log("Could not download $name: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        log("LWJGL arm64 natives: ${dir.listFiles()?.size ?: 0} files on disk ($downloaded downloaded now).")
        return dir
    }

    /** Reads the release's asset names and direct download URLs from the GitHub API. No auth needed for a public repo. */
    private fun fetchAssetUrls(): Map<String, String> {
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (connection.responseCode == 404) return emptyMap()
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            val body = connection.inputStream.bufferedReader().readText()
            val json = org.json.JSONObject(body)
            val assets = json.getJSONArray("assets")
            val result = mutableMapOf<String, String>()
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith(".so")) result[name] = asset.getString("browser_download_url")
            }
            return result
        } finally {
            connection.disconnect()
        }
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
            connection.inputStream.use { input -> partial.outputStream().use { output -> input.copyTo(output) } }
            check(partial.renameTo(target)) { "could not save the file" }
        } finally {
            connection.disconnect()
        }
    }
}

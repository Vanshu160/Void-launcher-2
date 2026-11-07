package com.voidlauncher.app.data.download

import com.voidlauncher.app.data.remote.GameMetadataParser
import com.voidlauncher.app.data.remote.Http
import com.voidlauncher.app.domain.model.GameFile
import com.voidlauncher.app.domain.model.MinecraftVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

data class DownloadProgress(val stage: String, val done: Int, val total: Int) {
    val fraction: Float
        get() = if (total == 0) 0f else done.toFloat() / total
}

/**
 * Downloads what one Minecraft version needs into minecraft/: metadata, client jar,
 * libraries and assets. A file that already exists with the right SHA-1 is skipped,
 * so an interrupted download can simply be started again.
 */
class GameDownloader(private val minecraftRoot: File) {

    companion object {
        private const val PARALLEL_DOWNLOADS = 8
        private const val ASSET_BASE = "https://resources.download.minecraft.net"
    }

    suspend fun download(version: MinecraftVersion, onProgress: (DownloadProgress) -> Unit) {
        withContext(Dispatchers.IO) {
            if (version.metadataUrl.isEmpty()) {
                throw IOException("No download link saved for ${version.id}. Refresh the version list.")
            }

            onProgress(DownloadProgress("Version info", 0, 1))
            val metadataFile = ensure(
                GameFile(
                    relativePath = "versions/${version.id}/${version.id}.json",
                    url = version.metadataUrl,
                    sha1 = version.metadataSha1,
                ),
            )
            onProgress(DownloadProgress("Version info", 1, 1))
            val metadata = GameMetadataParser.parse(metadataFile.readText())

            onProgress(DownloadProgress("Client", 0, 1))
            ensure(metadata.client)
            onProgress(DownloadProgress("Client", 1, 1))

            val indexFile = ensure(metadata.assetIndex)

            downloadAll("Libraries", metadata.libraries, onProgress)

            val assets = GameMetadataParser.parseAssetIndex(indexFile.readText())
                .distinctBy { it.hash }
                .map { asset ->
                    val prefix = asset.hash.take(2)
                    GameFile(
                        relativePath = "assets/objects/$prefix/${asset.hash}",
                        url = "$ASSET_BASE/$prefix/${asset.hash}",
                        sha1 = asset.hash,
                    )
                }
            downloadAll("Assets", assets, onProgress)
        }
    }

    private suspend fun downloadAll(
        stage: String,
        files: List<GameFile>,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        val total = files.size
        val done = AtomicInteger(0)
        val gate = Semaphore(PARALLEL_DOWNLOADS)
        onProgress(DownloadProgress(stage, 0, total))
        coroutineScope {
            files.map { file ->
                async(Dispatchers.IO) {
                    gate.withPermit { ensure(file) }
                    onProgress(DownloadProgress(stage, done.incrementAndGet(), total))
                }
            }.awaitAll()
        }
    }

    /** Returns the local file, downloading it only when it is missing or its SHA-1 is wrong. */
    private fun ensure(file: GameFile): File {
        val target = File(minecraftRoot, file.relativePath)
        val expected = file.sha1
        if (expected != null && target.isFile && sha1Of(target).equals(expected, ignoreCase = true)) {
            return target
        }
        Http.download(file.url, target, expected)
        return target
    }

    private fun sha1Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

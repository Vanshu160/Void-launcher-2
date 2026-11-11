package com.voidlauncher.app.data.launch

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * LWJGL 3.3.3 native libraries for arm64 Android, built by this repo's CI
 * (.github/workflows/build-lwjgl-arm64.yml) from PojavLauncherTeam's Android build of LWJGL
 * (github.com/PojavLauncherTeam/lwjgl3, wip/rebase_3.3.3).
 *
 * The CI publishes every build under its own unique tag (lwjgl-3.3.3-arm64-run<N>) and never
 * overwrites a tag. Nothing here hardcodes a tag. [ensure] tries the newest complete GitHub release
 * first, then up to three older complete releases, and only then falls back to the verified offline cache.
 *
 * A natives folder is only returned when every required library is present, is a valid 64-bit
 * AArch64 ET_DYN shared library, and matches its SHA-256. The folder's manifest.json records those
 * hashes, so an offline launch can re-check the cached files instead of trusting their names.
 */
object Lwjgl333Natives {
    private const val RELEASES_API = "https://api.github.com/repos/Vanshu160/Void-launcher-2/releases"
    private const val TAG_PREFIX = "lwjgl-3.3.3-arm64"
    private val SAFE_TAG = Regex("^lwjgl-3\\.3\\.3-arm64[A-Za-z0-9._-]*$")
    private val RUN_NUMBER = Regex("-run(\\d+)$")
    private val SHA256_HEX = Regex("^[0-9a-f]{64}$")
    private const val NATIVES_ROOT = "natives/lwjgl-3.3.3-arm64"
    private const val MANIFEST_NAME = "manifest.json"
    private const val PAGE_SIZE = 100
    private const val MAX_PAGES = 5
    /** The newest complete release plus up to three older complete releases. */
    private const val MAX_RELEASE_ATTEMPTS = 4
    private const val ELF_TYPE_DYN = 3
    private const val ELF_MACHINE_AARCH64 = 0xB7

    /**
     * Serialises every [ensure] call in this process. Without it, two launches could download into the
     * same .part file at the same time and one would overwrite the other. [ensure] is a blocking call
     * (it runs on Dispatchers.IO in the caller), so a plain monitor is used instead of a suspending Mutex.
     */
    private val LOCK = Any()

    /**
     * Exactly the libraries Minecraft 1.21.11's LWJGL 3.3.3 classpath needs from java.library.path.
     * Each name is what LWJGL asks the Android loader for:
     *  - liblwjgl.so         core, Library.loadSystem(..., "lwjgl")
     *  - liblwjgl_opengl.so  GL.java, Library.loadSystem(..., "lwjgl_opengl")
     *  - liblwjgl_stb.so     LibSTB.java, Platform.mapLibraryNameBundled("lwjgl_stb")
     *  - liblwjgl_tinyfd.so  TinyFileDialogs.java, Library.loadSystem(..., "lwjgl_tinyfd")
     *  - libfreetype.so      FreeType.java, Library.loadNative(..., "freetype")
     * Not part of this list: libglfw.so and libSDL3.so (built into the APK by app/src/main/cpp),
     * libopenal.so (not built by this CI), and jemalloc (not used: org.lwjgl.system.allocator=system).
     * Extra libraries in a release (nanovg, opengles, vma, shaderc) are ignored.
     */
    private val REQUIRED_LIBRARIES = listOf(
        "liblwjgl.so",
        "liblwjgl_opengl.so",
        "liblwjgl_stb.so",
        "liblwjgl_tinyfd.so",
        "libfreetype.so",
    )

    private class Asset(val url: String, val sha256: String?)

    private class Release(val tag: String, val assets: Map<String, Asset>)

    /**
     * Returns a natives folder that holds every required library, or null. Tries the newest complete
     * release, then up to three older complete releases. Each attempt either yields a fully validated
     * folder or is discarded. If all attempts fail, falls back to a previously downloaded folder whose
     * files still verify, so an offline launch keeps working. Never returns a folder missing a library.
     */
    fun ensure(minecraftRoot: File, log: (String) -> Unit): File? = synchronized(LOCK) {
        val root = File(minecraftRoot, NATIVES_ROOT)
        val candidates = completeReleases(log).iterator()
        var attempts = 0
        var listingFailed = false
        while (attempts < MAX_RELEASE_ATTEMPTS) {
            val release = try {
                if (!candidates.hasNext()) break
                candidates.next()
            } catch (e: Exception) {
                listingFailed = true
                val where = if (attempts == 0) "" else " after $attempts attempt(s)"
                log("Could not check GitHub for LWJGL arm64 natives$where: ${reason(e)}")
                break
            }
            attempts++
            log("LWJGL release attempt $attempts of $MAX_RELEASE_ATTEMPTS: ${release.tag}.")
            val dir = installRelease(root, release, log)
            if (dir != null) return@synchronized dir
            log("Release ${release.tag} failed (attempt $attempts of $MAX_RELEASE_ATTEMPTS).")
        }
        when {
            // The listing failure has already been logged above, with the number of attempts made.
            listingFailed -> Unit
            attempts == 0 ->
                log("No published LWJGL 3.3.3 arm64 release contains all required libraries (${REQUIRED_LIBRARIES.joinToString()}).")
            attempts < MAX_RELEASE_ATTEMPTS ->
                log("No further complete release is available after $attempts attempt(s).")
            else ->
                log("All $MAX_RELEASE_ATTEMPTS release attempts failed. Falling back to the verified offline cache.")
        }
        cachedCompleteDir(root, log)
    }

    /**
     * Downloads and validates the required libraries of [release] into its own tag folder. Returns the
     * folder only when all of them are valid. Stops at the first library that cannot be made valid, so
     * the remaining libraries of a broken release are not downloaded. Existing valid files are never touched.
     */
    private fun installRelease(root: File, release: Release, log: (String) -> Unit): File? {
        val dir = File(root, release.tag)
        dir.mkdirs()
        val hashes = mutableMapOf<String, String>()
        var downloaded = 0
        for (name in REQUIRED_LIBRARIES) {
            val target = File(dir, name)
            val asset = release.assets.getValue(name)
            val problem = problemWith(target, asset.sha256)
            if (problem == null) {
                val hash = hashOrNull(target)
                if (hash != null) {
                    hashes[name] = hash
                    log("LWJGL native $name: valid, already on disk (${release.tag}).")
                    continue
                }
                log("LWJGL native $name: could not be re-read for hashing. Downloading from ${release.tag}.")
            } else {
                log("LWJGL native $name: $problem. Downloading from ${release.tag}.")
            }
            try {
                hashes[name] = download(asset, target)
                downloaded++
                log("LWJGL native $name: downloaded and validated.")
            } catch (e: Exception) {
                log("LWJGL native $name: download failed: ${reason(e)}. Skipping the remaining libraries of ${release.tag}.")
                return null
            }
        }
        try {
            writeManifest(dir, hashes)
        } catch (e: Exception) {
            log("Could not write the natives manifest, so offline use of this copy is unavailable: ${reason(e)}")
        }
        log("LWJGL arm64 natives ready from ${release.tag}: all ${REQUIRED_LIBRARIES.size} required libraries valid ($downloaded downloaded now).")
        return dir
    }

    /**
     * Lazily yields, newest first, every non-draft release that has all required libraries. Release
     * pages are only fetched as the caller asks for more releases.
     */
    private fun completeReleases(log: (String) -> Unit): Sequence<Release> = sequence {
        for (page in 1..MAX_PAGES) {
            val releases = JSONArray(httpGetText("$RELEASES_API?per_page=$PAGE_SIZE&page=$page"))
            for (i in 0 until releases.length()) {
                val entry = releases.getJSONObject(i)
                val tag = entry.optString("tag_name")
                if (entry.optBoolean("draft") || !tag.startsWith(TAG_PREFIX)) continue
                if (!SAFE_TAG.matches(tag)) {
                    log("Skipping a release with an unexpected tag name.")
                    continue
                }
                val assets = parseAssets(entry.optJSONArray("assets"))
                val missing = REQUIRED_LIBRARIES.filterNot { assets.containsKey(it) }
                if (missing.isEmpty()) {
                    yield(Release(tag, assets))
                } else {
                    log("Release $tag is missing ${missing.joinToString()}; checking older releases.")
                }
            }
            if (releases.length() < PAGE_SIZE) break
        }
    }

    /** Keeps only assets whose exact names are in [REQUIRED_LIBRARIES], so a name never becomes a path by accident. */
    private fun parseAssets(list: JSONArray?): Map<String, Asset> {
        val result = mutableMapOf<String, Asset>()
        if (list == null) return result
        for (j in 0 until list.length()) {
            val asset = list.getJSONObject(j)
            val name = asset.optString("name")
            if (name !in REQUIRED_LIBRARIES) continue
            val url = asset.optString("browser_download_url")
            if (!url.startsWith("https://")) continue
            val digest = asset.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
            if (digest != null && !SHA256_HEX.matches(digest)) continue
            result[name] = Asset(url, digest)
        }
        return result
    }

    /**
     * Returns the best verified folder under [root] for offline use, or null if none qualifies. Folders are
     * tried in a fixed order: highest run number first (lwjgl-3.3.3-arm64-run<N>), then tag name. A folder
     * qualifies when its manifest lists every required library and each file still verifies (valid ELF,
     * matching hash). Modification times are not used, so the choice does not depend on file-system timing.
     */
    private fun cachedCompleteDir(root: File, log: (String) -> Unit): File? {
        val candidates = root.listFiles()
            ?.filter { it.isDirectory && SAFE_TAG.matches(it.name) }
            ?.sortedWith(compareByDescending<File> { runNumber(it.name) }.thenByDescending { it.name })
            .orEmpty()
        for (dir in candidates) {
            val manifest = readManifest(File(dir, MANIFEST_NAME))
            if (manifest == null) {
                log("Cached LWJGL natives ${dir.name}: no manifest, skipped.")
                continue
            }
            val problems = REQUIRED_LIBRARIES.mapNotNull { name ->
                val expected = manifest[name] ?: return@mapNotNull "$name: not in manifest"
                problemWith(File(dir, name), expected)?.let { "$name: $it" }
            }
            if (problems.isEmpty()) {
                log("Using cached LWJGL arm64 natives ${dir.name}: all ${REQUIRED_LIBRARIES.size} required libraries verified.")
                return dir
            }
            log("Cached LWJGL natives ${dir.name} are not usable: ${problems.joinToString("; ")}.")
        }
        log("No complete, verified LWJGL arm64 natives are available.")
        return null
    }

    /** The build number in a tag such as lwjgl-3.3.3-arm64-run9 (9). Tags without one rank below every numbered build. */
    private fun runNumber(tag: String): Int =
        RUN_NUMBER.find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: -1

    private fun readManifest(file: File): Map<String, String>? {
        if (!file.isFile) return null
        return try {
            val json = JSONObject(file.readText())
            json.keys().asSequence().associateWith { json.getString(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun writeManifest(dir: File, hashes: Map<String, String>) {
        val json = JSONObject()
        for ((name, sha) in hashes) json.put(name, sha)
        val partial = File(dir, "$MANIFEST_NAME.part")
        partial.writeText(json.toString())
        moveAtomically(partial, File(dir, MANIFEST_NAME))
    }

    /**
     * Null when [file] is a valid library that matches [expectedSha256] (if known). Otherwise a short reason.
     * A file that cannot be read (I/O or permission error) is reported as "unreadable" rather than thrown,
     * so the caller can treat it like any other bad copy.
     */
    private fun problemWith(file: File, expectedSha256: String?): String? = try {
        when {
            !file.isFile -> "missing"
            file.length() == 0L -> "invalid (empty file)"
            !isArm64Elf(file) -> "invalid (not a 64-bit AArch64 ELF shared library)"
            expectedSha256 != null && sha256(file) != expectedSha256 -> "invalid (SHA-256 does not match)"
            else -> null
        }
    } catch (e: IOException) {
        "unreadable (${reason(e)})"
    } catch (e: SecurityException) {
        "unreadable (${reason(e)})"
    }

    /** SHA-256 of [file], or null if the file cannot be read. */
    private fun hashOrNull(file: File): String? = try {
        sha256(file)
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    /** True for a little-endian, 64-bit, ET_DYN (shared library) ELF file built for AArch64. */
    private fun isArm64Elf(file: File): Boolean {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < 20) return false
            val header = ByteArray(20)
            raf.readFully(header)
            val isElf = header[0] == 0x7F.toByte() && header[1] == 'E'.code.toByte() &&
                header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte()
            val is64Bit = header[4] == 2.toByte()
            val isLittleEndian = header[5] == 1.toByte()
            val type = (header[16].toInt() and 0xFF) or ((header[17].toInt() and 0xFF) shl 8)
            val machine = (header[18].toInt() and 0xFF) or ((header[19].toInt() and 0xFF) shl 8)
            return isElf && is64Bit && isLittleEndian && type == ELF_TYPE_DYN && machine == ELF_MACHINE_AARCH64
        }
    }

    /**
     * Downloads [asset] to a .part file, checks it, and only then moves it over [target].
     * A failed download never touches an existing file at [target]. Returns the file's SHA-256.
     */
    private fun download(asset: Asset, target: File): String {
        val partial = File(target.parentFile, "${target.name}.part")
        partial.delete()
        val connection = (URL(asset.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                    }
                }
            }
            val actual = hex(digest.digest())
            if (asset.sha256 != null) check(actual == asset.sha256) { "SHA-256 does not match GitHub's digest" }
            check(isArm64Elf(partial)) { "not a 64-bit AArch64 ELF shared library" }
            moveAtomically(partial, target)
            return actual
        } catch (e: Exception) {
            partial.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    /** Atomic on the same filesystem: either the new file replaces the old one, or the old one is untouched. */
    private fun moveAtomically(from: File, to: File) {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return hex(digest.digest())
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun reason(e: Exception): String = e.message ?: e.javaClass.simpleName

    /** GET for small JSON responses. A 403 is reported as a likely rate limit. */
    private fun httpGetText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            val code = connection.responseCode
            if (code == 403) error("GitHub refused the request (HTTP 403, possibly rate limited)")
            check(code == 200) { "HTTP $code" }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

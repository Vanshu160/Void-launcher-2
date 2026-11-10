package com.voidlauncher.app.data.launch

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * JNA's own official Android build of libjnidispatch.so, downloaded directly from Maven Central
 * as a .aar for the exact JNA version the running game requests, then extracted into a per-version
 * natives folder - the same pattern PojavLauncher's own MoJsonDownloader/NativesExtractor use
 * (TeamPojavLauncher/PojavLauncher, Tools.java and tasks/NativesExtractor.java).
 *
 * This exists because JNA discards a native dispatch library whose compiled-in version does not
 * match its own jar's version, and then falls back to self-extracting its own bundled build, which
 * targets glibc and fails on Android's bionic libc ("libc.so.6 not found"). A single static copy of
 * libjnidispatch.so bundled at build time (app/src/main/jniLibs) only works for the exact JNA
 * version it was built for, so each game version needs its own matching copy.
 */
object JnaNatives {
    private const val MAVEN_CENTRAL = "https://repo1.maven.org/maven2/"

    // Known-good AAR SHA1 hashes, read directly from Maven Central's own .sha1 files. Extend this
    // map (and nothing else) when a newer Minecraft version requires a JNA version not listed here.
    private val KNOWN_SHA1 = mapOf(
        "5.17.0" to "4134d63c53cbf9650b11ee950df093add29f14cf",
    )

    /**
     * Ensures libjnidispatch.so for [jnaVersion] is on disk, downloading and extracting it from
     * JNA's official .aar if needed. Returns the directory it was extracted into, or null if
     * [jnaVersion] is null (not listed in the game's metadata) or the download/extraction failed -
     * the caller should still try to launch, since the static jniLibs copy may work by chance for
     * some versions.
     */
    fun ensure(minecraftRoot: File, jnaVersion: String?, log: (String) -> Unit): File? {
        if (jnaVersion == null) {
            log("JNA version not listed in this game's metadata; skipping the per-version native dispatch library.")
            return null
        }
        val dir = File(minecraftRoot, "natives/jna-$jnaVersion-arm64")
        val marker = File(dir, "libjnidispatch.so")
        if (marker.isFile && marker.length() > 0) {
            log("JNA $jnaVersion native dispatch library already on disk.")
            return dir
        }
        val aarUrl = "${MAVEN_CENTRAL}net/java/dev/jna/jna/$jnaVersion/jna-$jnaVersion.aar"
        val aarFile = File(dir, "jna-$jnaVersion.aar.tmp")
        dir.mkdirs()
        try {
            download(aarUrl, aarFile)
            KNOWN_SHA1[jnaVersion]?.let { expected ->
                val actual = sha1(aarFile)
                check(actual.equals(expected, ignoreCase = true)) {
                    "SHA1 mismatch for jna-$jnaVersion.aar (expected $expected, got $actual)"
                }
            }
            val extracted = extractArm64Libs(aarFile, dir)
            check(extracted > 0) { "no jni/arm64-v8a/*.so entries found in jna-$jnaVersion.aar" }
            log("JNA $jnaVersion: extracted $extracted native file(s) from the official Android AAR (Maven Central).")
            return dir
        } catch (e: Exception) {
            log("Could not prepare JNA $jnaVersion native dispatch library: ${e.message ?: e.javaClass.simpleName}")
            return null
        } finally {
            aarFile.delete()
        }
    }

    /** Extracts every jni/arm64-v8a/*.so entry from the .aar (a zip file) into [destDir]. Returns the count. */
    private fun extractArm64Libs(aarFile: File, destDir: File): Int {
        val prefix = "jni/arm64-v8a/"
        var count = 0
        ZipInputStream(aarFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.startsWith(prefix)) {
                    val name = entry.name.removePrefix(prefix)
                    if (name.isNotEmpty() && !name.contains('/')) {
                        File(destDir, name).outputStream().use { out -> zip.copyTo(out) }
                        count++
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return count
    }

    private fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun download(url: String, target: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode} for $url" }
            connection.inputStream.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        } finally {
            connection.disconnect()
        }
    }
}

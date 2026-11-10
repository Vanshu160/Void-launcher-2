#!/usr/bin/env bash
# Void Launcher 0.8.12 - the real JNA fix: download JNA's own official Android AAR for the exact
# version Minecraft requests, and extract libjnidispatch.so from it, instead of relying on a
# single static copy bundled at build time.
#
# ROOT CAUSE (confirmed by reading PojavLauncher's actual source, TeamPojavLauncher/PojavLauncher,
# commit b32a74357b1f064193beebfe1baa12c226568353):
#   - The libjnidispatch.so we bundled in jniLibs/arm64-v8a is a byte-identical copy of Pojav's own
#     bundled copy (confirmed by sha256), which Pojav's own NativesExtractor.java explicitly treats
#     as a fallback that "may be too old" (see its LIBRARY_BLACKLIST comment).
#   - Pojav never relies on that static copy for the actual game. Its MoJsonDownloader.java does this
#     for every version: if the game's own jna library is >= 5.13.0 (ours is 5.17.0, confirmed from
#     Mojang's version metadata), it leaves the version alone and downloads JNA's own official .aar
#     from Maven Central (net/java/dev/jna/jna/<version>/jna-<version>.aar), then extracts
#     jni/arm64-v8a/libjnidispatch.so from inside it into a per-version natives folder, which is put
#     on java.library.path and jna.boot.library.path AHEAD of the static jniLibs copy.
#   - JNA discards a native library whose compiled-in version does not match its own jar's version,
#     which is exactly why our static (older/mismatched) copy was ignored and JNA fell back to
#     self-extracting its own bundled glibc build - which then failed with "libc.so.6 not found".
#
# THE FIX: a per-version AAR download (JnaNatives.kt), the same pattern already used for LWJGL natives
# (Lwjgl333Natives.kt), using JNA's own officially published Android build - not a custom NDK compile.
# Confirmed to exist on Maven Central (https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.17.0/jna-5.17.0.aar).
#
# Run from the ROOT of your Void-launcher-2 repository.
set -e

if [ ! -d .git ] || [ ! -f app/build.gradle.kts ]; then
  echo "ERROR: run this inside the Void-launcher-2 repository folder."
  exit 1
fi

LAUNCH=app/src/main/java/com/voidlauncher/app/data/launch

# ---------------------------------------------------------------------------
# 1. New file: JnaNatives.kt
cat > "$LAUNCH/JnaNatives.kt" << 'KT_EOF'
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
KT_EOF

# ---------------------------------------------------------------------------
# 2. Patch GameLauncher.kt: extract the JNA version from the metadata, call JnaNatives.ensure,
#    and put its directory ahead of the LWJGL/APK natives dirs on every library path.
F="$LAUNCH/GameLauncher.kt"
python3 - "$F" << 'PY'
import re, sys
path = sys.argv[1]
text = open(path).read()

# 2a. Add a jnaVersion() helper next to the existing lwjglVersion() helper.
marker_fn = '''    /** The LWJGL version the game asks for, read from its metadata. Null if it is not listed. */
    private fun lwjglVersion(json: JSONObject): String? {
        val libraries = json.optJSONArray("libraries") ?: return null
        for (i in 0 until libraries.length()) {
            val name = libraries.getJSONObject(i).optString("name")
            if (name.startsWith("org.lwjgl:lwjgl:")) return name.removePrefix("org.lwjgl:lwjgl:")
        }
        return null
    }'''
addition_fn = marker_fn + '''

    /** The JNA version the game asks for, read from its metadata. Null if it is not listed. */
    private fun jnaVersion(json: JSONObject): String? {
        val libraries = json.optJSONArray("libraries") ?: return null
        for (i in 0 until libraries.length()) {
            val name = libraries.getJSONObject(i).optString("name")
            if (name.startsWith("net.java.dev.jna:jna:")) return name.removePrefix("net.java.dev.jna:jna:")
        }
        return null
    }'''
if marker_fn not in text:
    print("ERROR: could not find lwjglVersion() in GameLauncher.kt. Send me this message - file may have changed.")
    sys.exit(1)
if "fun jnaVersion(" not in text:
    text = text.replace(marker_fn, addition_fn, 1)

# 2b. Log the JNA version, same style as the existing LWJGL version log line.
marker_log = '        AppLog.write("LWJGL version in this game: ${lwjglVersion(json) ?: "not listed"}")'
addition_log = marker_log + '\n        AppLog.write("JNA version in this game: ${jnaVersion(json) ?: "not listed"}")'
if marker_log not in text:
    print("ERROR: could not find the LWJGL version log line in GameLauncher.kt. Send me this message.")
    sys.exit(1)
if 'JNA version in this game' not in text:
    text = text.replace(marker_log, addition_log, 1)

# 2c. Call JnaNatives.ensure right after Lwjgl333Natives.ensure.
marker_ensure = '        val lwjglNativesDir = Lwjgl333Natives.ensure(minecraftRoot) { AppLog.write(it) }'
addition_ensure = marker_ensure + '\n        val jnaNativesDir = JnaNatives.ensure(minecraftRoot, jnaVersion(json)) { AppLog.write(it) }'
if marker_ensure not in text:
    print("ERROR: could not find the Lwjgl333Natives.ensure call in GameLauncher.kt. Send me this message.")
    sys.exit(1)
if 'val jnaNativesDir' not in text:
    text = text.replace(marker_ensure, addition_ensure, 1)

# 2d. Put jnaNativesDir first on the combined java.library.path / jna.boot.library.path string
#     (JNA must find the matching native library before it would otherwise self-extract or fall
#     through to the static jniLibs copy).
marker_libpath = '        val libraryPathDirs = listOfNotNull(lwjglNativesDir, nativeDir).joinToString(":") { it.absolutePath }'
addition_libpath = '        val libraryPathDirs = listOfNotNull(jnaNativesDir, lwjglNativesDir, nativeDir).joinToString(":") { it.absolutePath }'
if marker_libpath not in text:
    print("ERROR: could not find the libraryPathDirs line in GameLauncher.kt. Send me this message.")
    sys.exit(1)
text = text.replace(marker_libpath, addition_libpath, 1)

# 2e. Also put jnaNativesDir on LD_LIBRARY_PATH, same priority as the LWJGL natives dir.
marker_ld = '''        ).let { base -> if (lwjglNativesDir != null) listOf(lwjglNativesDir) + base else base }
            .joinToString(":") { it.absolutePath }'''
addition_ld = '''        ).let { base -> listOfNotNull(jnaNativesDir, lwjglNativesDir) + base }
            .joinToString(":") { it.absolutePath }'''
if marker_ld not in text:
    print("ERROR: could not find the LD_LIBRARY_PATH construction in GameLauncher.kt. Send me this message.")
    sys.exit(1)
text = text.replace(marker_ld, addition_ld, 1)

open(path, "w").write(text)
print("GameLauncher.kt patched.")
PY

# ---------------------------------------------------------------------------
# 3. Correct the checklist and third-party notes to reflect the real fix.
if [ -f CHECKLIST.md ]; then
  python3 - << 'PY'
path = "CHECKLIST.md"
text = open(path).read()
old = "- [x] JNA fix: PojavLauncher's own Android-built libjnidispatch.so (arm64) added to jniLibs"
new = ("- [x] JNA fix (corrected): the static jniLibs/arm64-v8a/libjnidispatch.so copy was confirmed "
       "insufficient (PojavLauncher itself treats it as a fallback only). Real fix: download JNA's own "
       "official Android .aar from Maven Central for the exact version the game requests, and extract "
       "a matching libjnidispatch.so per game version (JnaNatives.kt), same pattern as the LWJGL natives.")
if old in text and new not in text:
    text = text.replace(old, new, 1)
    open(path, "w").write(text)
    print("CHECKLIST.md updated.")
else:
    print("CHECKLIST.md line not found or already updated - no change made. Check it manually.")
PY
fi

if [ -f THIRD_PARTY.md ]; then
  cat >> THIRD_PARTY.md << 'MD_EOF'

## JNA native dispatch library (per game version, downloaded from Maven Central)
- **Source:** the JNA project's own official Android build, published alongside the regular jar as
  `net/java/dev/jna/jna/<version>/jna-<version>.aar` on Maven Central (not a third-party rebuild).
- **License:** JNA is dual licensed Apache-2.0 / LGPL-2.1 (https://github.com/java-native-access/jna)
- **How it's used:** `JnaNatives.kt` downloads the .aar for the JNA version the running game's
  metadata requests, verifies its SHA1 against a short pinned list, and extracts
  `jni/arm64-v8a/libjnidispatch.so` into a per-version natives folder - the exact approach
  PojavLauncher's own `tasks/NativesExtractor.java` and `tasks/MoJsonDownloader.java` use
  (TeamPojavLauncher/PojavLauncher, confirmed by reading that source directly).
- **Why not keep using the static jniLibs copy:** JNA discards a native dispatch library whose
  compiled-in version does not match its own jar, so a single static copy only works for the one
  JNA version it was built against. PojavLauncher's own source documents this copy as a fallback
  that "may be too old" and always prefers a version-matched download when the game needs JNA
  5.13.0 or newer (our games need 5.17.0).
MD_EOF
  echo "THIRD_PARTY.md updated."
fi

# ---------------------------------------------------------------------------
echo
echo "Done. Milestone 0.8.12 written: real JNA native-dispatch fix (JnaNatives.kt)."
echo
echo "What changed:"
echo "  - NEW   $LAUNCH/JnaNatives.kt"
echo "  - EDIT  $LAUNCH/GameLauncher.kt (jnaVersion() helper, JnaNatives.ensure() call, library paths)"
echo "  - EDIT  CHECKLIST.md, THIRD_PARTY.md (corrected JNA fix description)"
echo
echo "Review the diff before committing:"
echo "  git diff"
echo
echo "If it looks right:"
echo "  git add -A && git commit -m \"Milestone 0.8.12: real JNA fix - download JNA's own Android AAR per game version\" && git push"

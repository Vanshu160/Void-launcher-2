#!/usr/bin/env bash
# Void Launcher 0.8.10 / Phase 3 start - root-cause fixes for the three problems you found in the log.
#
# 1. LWJGL "platform/architecture mismatch" (classpath says linux/x64):
#    Root cause: the game's classpath only ever had Mojang's x64 LWJGL natives jar. We never had
#    any arm64 LWJGL 3.3.3 natives to put on java.library.path (the 3.2.3 jars we tried earlier are
#    the WRONG LWJGL version and were correctly removed in milestone 0.8.8).
#    There is no published arm64 build of LWJGL 3.3.3 anywhere (checked MojoLauncher's release and
#    the official LWJGL project). PojavLauncherTeam maintains a real, working Android build of LWJGL
#    at github.com/PojavLauncherTeam/lwjgl3 (branch wip/rebase_3.3.3), with its own CI workflow
#    (.github/workflows/build-android.yml -> ci_build_android.bash) that compiles LWJGL's native
#    code against the NDK for arm64, armeabi-v7a, x86 and x86_64. This script adds a NEW, separate
#    GitHub Actions workflow in THIS repo that runs that exact build for arm64 and publishes the
#    output (.so files) as a GitHub Release here, named lwjgl-3.3.3-arm64. This is a real compile,
#    not a download of someone else's binary, and it can fail or need iteration - CI will show that.
#
# 2. JNA "dlopen failed: library libc.so.6 not found":
#    Root cause: the JNA jar on the classpath (used by Minecraft/oshi for hardware info) tries to
#    extract its OWN bundled desktop-Linux libjnidispatch.so, which was built against glibc and
#    needs libc.so.6. Android's bionic libc has no file by that name, so it fails.
#    Fix: bundle PojavLauncher's own prebuilt Android libjnidispatch.so (arm64, built against
#    bionic, already used in production by PojavLauncher) in our APK's jniLibs. JNA's Native class
#    checks jna.boot.library.path (already set by GameLauncher) BEFORE trying to extract its own
#    copy, so once our libjnidispatch.so is found there, it is used instead and the dlopen error
#    goes away. This does not touch OSHI: once JNA itself loads, OSHI's calls through JNA either
#    work or fail on their own (Android does not expose the same /proc and sysfs files Linux does,
#    so OSHI may still warn about some hardware info - those are not the crash, just OSHI being
#    unable to find a file, and Minecraft does not need the OSHI info to run).
#
# 3. GLFW/OpenGL native libraries: we already BUILD libglfw.so and libSDL3.so for arm64 in CI
#    (milestone 0.7.0d, which passed). The "liblwjgl.so" and GLFW failure you saw were downstream
#    of problem 1: the LWJGL *core* native library (liblwjgl.so) does not exist for arm64 yet,
#    so LWJGL's org.lwjgl.glfw.GLFW class (which depends on liblwjgl.so loading first) never got
#    that far. Our own libglfw.so is correct and already in the APK; it cannot be tested until
#    LWJGL's own core native loads, which is what step 1 is building.
#
# This script:
#  - adds .github/workflows/build-lwjgl-arm64.yml (a new workflow, run manually or on demand)
#  - adds the Android-compatible libjnidispatch.so to app/src/main/jniLibs/arm64-v8a
#  - adds a downloader (Lwjgl333Natives.kt) that fetches the built LWJGL .so files from our own
#    release once the workflow has produced them, the same way GameRunner uses a per-version
#    natives directory
#  - wires GameLauncher to point java.library.path, jna.boot.library.path and
#    org.lwjgl.librarypath at that directory, same property names PojavLauncher's GameRunner uses
#
# IMPORTANT: step 1 (the LWJGL build) must run and succeed in GitHub Actions BEFORE its natives
# exist anywhere. Until then, Lwjgl333Natives.ensure() will report "not available yet" in the log
# and the game will still fail at the same LWJGL step - that is expected, not a bug in this script.
#
# Run from the ROOT of your Void-launcher-2 repository.
set -e

if [ ! -d .git ] || [ ! -f app/build.gradle.kts ]; then
  echo "ERROR: run this inside the Void-launcher-2 repository folder."
  exit 1
fi

mkdir -p .github/workflows app/src/main/jniLibs/arm64-v8a app/src/main/java/com/voidlauncher/app/data/launch

# ---------------------------------------------------------------------------
# 1. New workflow: builds LWJGL 3.3.3 for arm64 from PojavLauncherTeam's own build, and publishes
#    the native libraries as a GitHub Release in this repo.
cat > .github/workflows/build-lwjgl-arm64.yml << 'YML_EOF'
name: Build LWJGL 3.3.3 (arm64, from PojavLauncherTeam's own Android build)

# Manual, because this is a slow native compile (libffi + FreeType + LWJGL's C modules) and is
# only needed again if we change the LWJGL version or the upstream build changes.
on:
  workflow_dispatch:

permissions:
  contents: write

jobs:
  build:
    runs-on: ubuntu-22.04
    timeout-minutes: 60
    env:
      ANDROID: "1"
      LWJGL_BUILD_OFFLINE: "1"
      LWJGL_BUILD_ARCH: arm64
      SKIP_LIBFFI: "0"
      SKIP_FREETYPE: "0"
    steps:
      - name: Checkout PojavLauncherTeam/lwjgl3 (wip/rebase_3.3.3, their own working Android build)
        uses: actions/checkout@v6
        with:
          repository: PojavLauncherTeam/lwjgl3
          ref: wip/rebase_3.3.3
          path: lwjgl3

      - name: Set up JDK 8 (the build's own Ant script requires it)
        uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: 8

      - name: Set up Android NDK
        uses: android-actions/setup-android@v3

      - name: Install the exact NDK version this project uses
        run: echo "y" | sdkmanager --install "ndk;29.0.14206865"

      - name: Install Ant
        run: sudo apt-get update && sudo apt-get install -y ant

      - name: Run PojavLauncherTeam's own Android build script for arm64
        working-directory: lwjgl3
        env:
          ANDROID_NDK_HOME: ${{ env.ANDROID_SDK_ROOT }}/ndk/29.0.14206865
        run: bash ci_build_android.bash

      - name: Collect the built native libraries
        run: |
          mkdir -p out
          cp lwjgl3/bin/out/*.so out/ 2>/dev/null || true
          ls -la out

      - name: Upload as a build artifact (always, so a failed release step still leaves this)
        uses: actions/upload-artifact@v7
        with:
          name: lwjgl-3.3.3-arm64-natives
          path: out/*.so
          if-no-files-found: error

      - name: Publish as a GitHub Release in this repository
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          TAG="lwjgl-3.3.3-arm64"
          gh release delete "$TAG" --yes 2>/dev/null || true
          git tag -f "$TAG"
          git push -f origin "$TAG"
          gh release create "$TAG" out/*.so \
            --title "LWJGL 3.3.3 native libraries (arm64 Android)" \
            --notes "Built from PojavLauncherTeam/lwjgl3 at wip/rebase_3.3.3 using their own ci_build_android.bash, for arm64 (aarch64-linux-android) only. These are the real LWJGL 3.3.3 natives for Android, not a substitute version." \
            --prerelease
YML_EOF

# ---------------------------------------------------------------------------
# 2. JNA fix: download PojavLauncher's prebuilt, Android-compatible libjnidispatch.so (arm64) and
#    bundle it in the APK. JNA is Apache-2.0/LGPL-2.1 dual licensed; this binary is built from that
#    project's own native/platform code (no Void Launcher code involved).
echo "Downloading libjnidispatch.so (arm64, Android build) from the PojavLauncher fork..."
curl -fsSL -o app/src/main/jniLibs/arm64-v8a/libjnidispatch.so \
  "https://raw.githubusercontent.com/Vanshu160/PojavLauncher/b32a74357b1f064193beebfe1baa12c226568353/app_pojavlauncher/src/main/jniLibs/arm64-v8a/libjnidispatch.so"
SIZE=$(wc -c < app/src/main/jniLibs/arm64-v8a/libjnidispatch.so)
if [ "$SIZE" -lt 10000 ]; then
  echo "ERROR: libjnidispatch.so download looks wrong (only $SIZE bytes). Not continuing."
  exit 1
fi
echo "Downloaded libjnidispatch.so ($SIZE bytes)."

# ---------------------------------------------------------------------------
# 3. Downloader for the LWJGL 3.3.3 arm64 natives, once the workflow above has produced them.
cat > app/src/main/java/com/voidlauncher/app/data/launch/Lwjgl333Natives.kt << 'KT_EOF'
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
KT_EOF

# ---------------------------------------------------------------------------
# 4. Wire GameLauncher to use the LWJGL natives directory and PojavLauncher's exact property names
#    for it (java.library.path, jna.boot.library.path, org.lwjgl.librarypath), same as GameRunner.
cat > app/src/main/java/com/voidlauncher/app/data/launch/GameLauncher.kt << 'KT_EOF'
package com.voidlauncher.app.data.launch

import android.content.Context
import android.os.Build
import android.system.Os
import com.voidlauncher.app.NativeBridge
import com.voidlauncher.app.data.log.AppLog
import com.voidlauncher.app.data.remote.GameMetadataParser
import com.voidlauncher.app.data.runtime.JavaRuntimeManager
import com.voidlauncher.app.data.runtime.JavaRuntimeSpec
import net.kdt.pojavlaunch.utils.JREUtils
import net.kdt.pojavlaunch.utils.jre.JavaRunner
import org.json.JSONObject
import java.io.File

/**
 * Starts Minecraft the same way PojavLauncher's JavaRunner.startJvm and GameRunner do.
 * The JVM runs on its own thread because it does not return.
 */
class GameLauncher(private val context: Context) {

    private val minecraftRoot = File(context.filesDir, "minecraft")
    private val gameDir = File(minecraftRoot, "game")
    private val runtimes = JavaRuntimeManager(context)

    /** Loads the native libraries. Throws UnsatisfiedLinkError if they are missing from the APK. */
    private fun loadNative() {
        System.loadLibrary("mojoexec")
        System.loadLibrary("mojort")
        AppLog.write("Native bridge: ${NativeBridge.nativeVersion()}")
    }

    /** The LWJGL version the game asks for, read from its metadata. Null if it is not listed. */
    private fun lwjglVersion(json: JSONObject): String? {
        val libraries = json.optJSONArray("libraries") ?: return null
        for (i in 0 until libraries.length()) {
            val name = libraries.getJSONObject(i).optString("name")
            if (name.startsWith("org.lwjgl:lwjgl:")) return name.removePrefix("org.lwjgl:lwjgl:")
        }
        return null
    }

    /** Starts the game. Returns after starting the JVM thread. Progress goes to [onStatus]. */
    fun launch(versionId: String, ramMb: Int = 2048, onStatus: (String) -> Unit) {
        val metaFile = File(minecraftRoot, "versions/$versionId/$versionId.json")
        check(metaFile.isFile) { "Game files for $versionId are not downloaded." }
        val json = JSONObject(metaFile.readText())
        val metadata = GameMetadataParser.parse(metaFile.readText())

        val spec = JavaRuntimeSpec.forMajor(metadata.javaMajorVersion)
        check(runtimes.isInstalled(spec)) { "Java ${spec.majorVersion} is not installed yet." }
        val runtimeHome = runtimes.home(spec)
        val vmPath = runtimeHome.walkTopDown().firstOrNull { it.isFile && it.name == "libjvm.so" }
            ?: error("libjvm.so was not found in the Java runtime.")
        AppLog.write("libjvm.so: ${vmPath.absolutePath}")
        AppLog.write("LWJGL version in this game: ${lwjglVersion(json) ?: "not listed"}")

        gameDir.mkdirs()
        onStatus("Checking LWJGL arm64 natives...")
        val lwjglNativesDir = Lwjgl333Natives.ensure(minecraftRoot) { AppLog.write(it) }

        val classFiles = listOf(File(minecraftRoot, metadata.client.relativePath)) +
            metadata.libraries.map { File(minecraftRoot, it.relativePath) }
        val classpath = classFiles.joinToString(":") { it.absolutePath }
        AppLog.write("Classpath entries: ${classFiles.size}")

        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val lwjglExtractDir = File(context.cacheDir, "lwjgl_native/$versionId").apply { mkdirs() }

        // Same property names as PojavLauncher (JavaRunner.getJavaArgs and GameRunner), including the
        // per-version natives directory pattern GameRunner uses for libraries not bundled in the APK.
        val libraryPathDirs = listOfNotNull(lwjglNativesDir, nativeDir).joinToString(":") { it.absolutePath }
        val javaArgs = mutableListOf(
            "-Xms512M",
            "-Xmx${ramMb}M",
            "-Djava.home=${runtimeHome.absolutePath}",
            "-Djava.io.tmpdir=${context.cacheDir.absolutePath}",
            "-Djava.library.path=$libraryPathDirs",
            "-Djna.boot.library.path=$libraryPathDirs",
            "-Duser.home=${gameDir.absolutePath}",
            "-Duser.language=en",
            "-Duser.country=US",
            "-Dos.name=Linux",
            "-Dos.version=Android-${Build.VERSION.RELEASE}",
            "-Dpojav.path.minecraft=${minecraftRoot.absolutePath}",
            "-Dorg.lwjgl.vulkan.libname=libvulkan.so",
            "-Dorg.lwjgl.spvc.libname=spirv-cross-c-shared",
            "-Dorg.lwjgl.sdl.libname=${File(nativeDir, "libSDL3.so").absolutePath}",
            "-Dorg.lwjgl.system.allocator=system",
            "-Dorg.lwjgl.system.SharedLibraryExtractPath=${lwjglExtractDir.absolutePath}",
        ).apply {
            if (lwjglNativesDir != null) add("-Dorg.lwjgl.librarypath=${lwjglNativesDir.absolutePath}")
        } + listOf(
            // Default process launching does not work on Android.
            "-Djdk.lang.Process.launchMechanism=FORK",
            "-Djava.class.path=$classpath",
            // If the JVM itself crashes, it writes its report here. The app copies it into the log on next start.
            "-XX:ErrorFile=${File(context.filesDir, "jvm-error-%p.log").absolutePath}",
        )
        AppLog.write(
            "JVM properties: " + javaArgs
                .filterNot { it.startsWith("-Djava.class.path") }
                .joinToString(" | ")
        )

        val values = mapOf(
            "auth_player_name" to "Player",
            "version_name" to versionId,
            "game_directory" to gameDir.absolutePath,
            "assets_root" to File(minecraftRoot, "assets").absolutePath,
            "assets_index_name" to metadata.assetIndexId,
            "auth_uuid" to "00000000000000000000000000000000",
            "auth_access_token" to "0",
            "auth_xuid" to "0",
            "clientid" to "0",
            "user_type" to "legacy",
            "version_type" to json.optString("type", "release"),
        )
        val gameArgs = mutableListOf<String>()
        json.optJSONObject("arguments")?.optJSONArray("game")?.let { list ->
            for (i in 0 until list.length()) {
                val arg = list.opt(i)
                if (arg is String) gameArgs += substitute(arg, values)
            }
        }
        if (gameArgs.isEmpty()) {
            // Older versions use one string of arguments instead.
            json.optString("minecraftArguments").split(" ").filter { it.isNotEmpty() }.forEach {
                gameArgs += substitute(it, values)
            }
        }
        val mainClass = json.getString("mainClass")

        onStatus("Loading native libraries...")
        loadNative()

        // Same environment the PojavLauncher JVM expects.
        val libsDir = checkNotNull(vmPath.parentFile?.parentFile)
        val ldPath = listOf(
            libsDir,
            nativeDir,
            vmPath.parentFile,
            File(libsDir, "jli"),
        ).let { base -> if (lwjglNativesDir != null) listOf(lwjglNativesDir) + base else base }
            .joinToString(":") { it.absolutePath }
        Os.setenv("JAVA_HOME", runtimeHome.absolutePath, true)
        Os.setenv("HOME", gameDir.absolutePath, true)
        Os.setenv("TMPDIR", context.cacheDir.absolutePath, true)
        Os.setenv("LD_LIBRARY_PATH", ldPath, true)
        JREUtils.setLdLibraryPath(ldPath)
        AppLog.write("Environment set. LD_LIBRARY_PATH: $ldPath")

        onStatus("Starting Java ${spec.majorVersion} and Minecraft $versionId...")
        Thread(null, {
            try {
                AppLog.write("JVM thread started")
                NativeBridge.installCrashLog(AppLog.path())
                AppLog.write("Native crash catcher installed")

                // Minecraft writes its logs to a relative folder, so start from the game folder.
                try {
                    val result = JREUtils.chdir(gameDir.absolutePath)
                    AppLog.write("Working folder set to ${gameDir.absolutePath} (result $result)")
                } catch (e: UnsatisfiedLinkError) {
                    AppLog.write("Could not set working folder: ${e.message}")
                }

                val javaArgsPtr = JavaRunner.nativeTransferArguments(javaArgs.toTypedArray())
                AppLog.write("JVM arguments transferred (${javaArgs.size} items)")
                val appArgsPtr = JavaRunner.nativeTransferArguments(gameArgs.toTypedArray())
                AppLog.write("Game arguments transferred (${gameArgs.size} items)")

                AppLog.write("Calling nativeLoadJVM for $mainClass")
                val ok = JavaRunner.nativeLoadJVM(vmPath.absolutePath, javaArgsPtr, mainClass, appArgsPtr, false)
                AppLog.write("nativeLoadJVM returned $ok")
                onStatus(if (ok) "Minecraft exited." else "Minecraft did not start.")
            } catch (e: Throwable) {
                AppLog.write("JVM thread error: ${e.message ?: e.javaClass.simpleName}")
                onStatus("Launch failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }, "minecraft-jvm", 64L * 1024 * 1024).start()
    }

    private fun substitute(text: String, values: Map<String, String>): String {
        var result = text
        for ((key, value) in values) result = result.replace("\${$key}", value)
        return result
    }
}
KT_EOF

# ---------------------------------------------------------------------------
cat >> THIRD_PARTY.md << 'MD_EOF'

## libjnidispatch.so (JNA native dispatch library, Android build)
- **Source:** PojavLauncher fork, pinned commit `b32a74357b1f064193beebfe1baa12c226568353`, path `app_pojavlauncher/src/main/jniLibs/arm64-v8a/libjnidispatch.so`
- **Upstream project:** JNA (https://github.com/java-native-access/jna), dual licensed Apache-2.0 / LGPL-2.1
- **Why:** the JNA jar on the game's classpath tries to extract its own desktop-Linux build of this library, which needs glibc's `libc.so.6` and fails on Android's bionic libc. This is a build of the same native code against the Android NDK, matching JNA's JNI entry points, used by PojavLauncher in production.

## LWJGL 3.3.3 native libraries (arm64 Android), built in this repo's own CI
- **Source:** PojavLauncherTeam/lwjgl3 (https://github.com/PojavLauncherTeam/lwjgl3), branch `wip/rebase_3.3.3`
- **License:** BSD 3-Clause (same as upstream LWJGL)
- **How it's built:** `.github/workflows/build-lwjgl-arm64.yml` runs that project's own `ci_build_android.bash` for arm64 and publishes the resulting `.so` files as a GitHub Release (`lwjgl-3.3.3-arm64`) in this repository. The app downloads them at first launch (`Lwjgl333Natives.kt`), the same per-version natives folder pattern PojavLauncher's own `GameRunner` uses.
- **Status:** this is a real native compile of third-party, in-progress (unreleased) upstream code. It needs to be run and verified in GitHub Actions before it produces anything; see CHECKLIST.md.
MD_EOF

cat >> CHECKLIST.md << 'MD_EOF'

## Root-cause fixes for the first launch crash (in progress)
- [x] Identified: LWJGL finds only Mojang's x64 natives jar on the classpath; no arm64 LWJGL 3.3.3 natives exist anywhere yet
- [x] Identified: JNA's bundled libjnidispatch.so needs glibc (`libc.so.6`), which Android does not have
- [x] JNA fix: PojavLauncher's own Android-built libjnidispatch.so (arm64) added to jniLibs
- [x] New CI workflow added: builds real LWJGL 3.3.3 arm64 natives from PojavLauncherTeam/lwjgl3 (wip/rebase_3.3.3), publishes them as a release in this repo
- [ ] That workflow run has succeeded (must be checked in GitHub Actions; this build is non-trivial and may need iteration)
- [ ] GameLauncher downloads and wires in the built LWJGL natives (code written; depends on the workflow succeeding)
- [ ] Tested on a phone: LWJGL loads without the platform-mismatch warning
- [ ] Tested on a phone: JNA/oshi initialize without the libc.so.6 error
- [ ] Tested on a phone: GLFW opens an on-screen window
MD_EOF

echo
echo "Done. Files written. Two separate things need to happen:"
echo "  A) git add . && git commit -m \"Root-cause fixes: Android JNA dispatch library, start LWJGL 3.3.3 arm64 CI build\" && git push"
echo "  B) In GitHub, go to Actions -> 'Build LWJGL 3.3.3 (arm64...)' -> Run workflow, on the main branch."
echo "     This is a slow native build (can take 20-40 minutes) and may fail the first time - that is"
echo "     useful information, not a problem to hide. Send me the run's result either way."

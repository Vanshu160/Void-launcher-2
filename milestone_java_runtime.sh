#!/usr/bin/env bash
# Void Launcher 0.6.0 - Java runtime download on first use (signed, verified).
# Run from the ROOT of your Void-launcher-2 repository.
set -e

if [ ! -d .git ] || [ ! -f app/build.gradle.kts ]; then
  echo "ERROR: run this inside the Void-launcher-2 repository folder."
  exit 1
fi

BASE=app/src/main/java/com/voidlauncher/app
mkdir -p "$BASE/data/runtime" "$BASE/ui/downloads" app/src/main/assets

# ---------------------------------------------------------------------------
cat > gradle/libs.versions.toml << 'TOML_EOF'
[versions]
agp = "9.5.0"
kotlin = "2.4.21"
composeBom = "2026.09.00"
activityCompose = "1.13.0"
lifecycle = "2.11.0"
navigationCompose = "2.10.2"
commonsCompress = "1.27.1"
xzJava = "1.10"

[libraries]
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
commons-compress = { group = "org.apache.commons", name = "commons-compress", version.ref = "commonsCompress" }
xz = { group = "org.tukaani", name = "xz", version.ref = "xzJava" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
TOML_EOF

# ---------------------------------------------------------------------------
cat > app/build.gradle.kts << 'KTS_EOF'
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.voidlauncher.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.voidlauncher.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    // Java runtime archives are tar.xz files.
    implementation(libs.commons.compress)
    implementation(libs.xz)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
KTS_EOF

# ---------------------------------------------------------------------------
# Public certificate from the MojoLauncher PojavLauncher fork (LGPL-3.0),
# used only to check signatures on downloaded Java runtimes. See THIRD_PARTY.md.
cat > app/src/main/assets/runtime-cert.pem << 'PEM_EOF'
-----BEGIN CERTIFICATE-----
MIIFfTCCA2WgAwIBAgIUcNhAOohcehzmPSVTm6OeKvuOy/EwDQYJKoZIhvcNAQEL
BQAwTjELMAkGA1UEBhMCUlUxFTATBgNVBAgMDEtvc3Ryb21hIG9ibDERMA8GA1UE
BwwIS29zdHJvbWExFTATBgNVBAoMDE1vam9MYXVuY2hlcjAeFw0yNjAxMDQxNDQ4
MDlaFw0yNzAxMDQxNDQ4MDlaME4xCzAJBgNVBAYTAlJVMRUwEwYDVQQIDAxLb3N0
cm9tYSBvYmwxETAPBgNVBAcMCEtvc3Ryb21hMRUwEwYDVQQKDAxNb2pvTGF1bmNo
ZXIwggIiMA0GCSqGSIb3DQEBAQUAA4ICDwAwggIKAoICAQCkDCSoiwbF9sbu0gig
b1glIMlpNm50DUVMRvEkvrptu/F9bM+EsFo3WegvK0uAWPXuwOnxJlX9lyNkIJfO
l1MuQuVUmwHcif0L5s6tyRzIc2kQtXCBRdhQf5LVShVF2IhZurYdxbBpqmTA02rN
9Y7G0EPOYZZbwzEDKPP4DCvI79mEpzql29Ib2BiWJ/qz6N8Y6rOYwUgjMdiVJR58
vkdMPoeG3Eynn8J99KhU6HQTsCMZ5R9p3+nGce3Q5vkDCp/n3TFV34P1iI6Jbumu
k501qAnmd/0Wn26c9yFExuAMX9X/U+giD967hc9xksmapfq2OkGeaodwkvouQoBv
K2ltjkxbfjp/UT26mINrTZIWN6TUaDXM0x+QLuNRRES1Vpb45BwiuTLoW69bvpcf
jVXENgS1L1SXZgYL+PLsGOPNmLEL/PZNab5uRIjm+q1P3UR6FuAR1BffkUK+x6fS
NB2kinug1IqQEiZy6TFuADRDUN8Bur9Pg/wqJE0aBRJxH8DXkaemkbkFXnqdpsAO
2ich32ybvP765YL6rcsGQLnkqWVnFKIcGCbOUGOlyaVA8mIWYbUx4kO6tOAqE5VA
s732Rmi5wzU3Je/e4nOtbHd0a15oPsLT8hYwcxfo/sf5PJB2LTuLiDduY+0VBrSy
xBhtR6v3h2zQiskGStjKL9G55wIDAQABo1MwUTAdBgNVHQ4EFgQUD5uS3Zgiz0JN
TUisDsQ1a+1wtYAwHwYDVR0jBBgwFoAUD5uS3Zgiz0JNTUisDsQ1a+1wtYAwDwYD
VR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAgEAUHDuWckdMmYEwlY0Y/e8
1fVOW/Hwfsh7d2T5fLgXHn2GJ1eT++FxOjeWI6ipN5+dW+VpeQ+oPoWF5Yg9YZq+
Ra57rqGPF7LHkXxCAiI7s3SuWZonfdE9PA5nbZ789vhHmMQvR9XhM0YXaChNmOrK
ETtuPTrjVUv14ijQ3D8szLz8BnytcBxue1Mqn3iOPGYU7flD64oyMobwHc7D6hwK
BsUFiH5s+Npz1LqrVcA/+qm+yGa5gH6pzLp1m1/oBajC8T/IEhKHOyqexwU+KbpN
J7NGfYY3/Ok+KJwnAHOp7pppFA1CNEdIAoxJfx6LZ9lzavKaB9Ug+FsM3RGNxtgl
3k4YNGXQ/TEW/Jn7LEqZPpBUxXPu4Z2+Kw80OG7HGd5M3XdVAZgMorol5lieGCHa
TQrqnvYXBljUYygfxaJzeXv7y3mFSxsOuPVzKPlVcMFbWKbkzeVwEq3m6ShAnKXd
ntA+cSBo0mvjkJ3RWOiGupiXO3hk1P0XIc+qxAFik+tl2VPCUuFa9irxIoq98pRN
h+6IOKG1fX9eWZPYJHmpNyH30j4RCd38jRPhQ4o8uv/TjjXnZ84EjAmMUe+ueDkB
MHMh08T8FA41puYnGDRvQWDLCPCuaaqtgJRz4gLUQyhRq8e+v40Lxv+5cwSOXn7l
+o4ykcAbwvWyYbM/AW/Nktw=
-----END CERTIFICATE-----
PEM_EOF

# ---------------------------------------------------------------------------
cat > "$BASE/data/runtime/SignatureVerifier.kt" << 'KT_EOF'
package com.voidlauncher.app.data.runtime

import java.io.File
import java.io.InputStream
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertificateFactory

/** Checks SHA256withRSA signatures against the publisher's certificate. */
class SignatureVerifier(private val publicKey: PublicKey) {

    fun verify(file: File, signature: ByteArray): Boolean {
        val check = Signature.getInstance("SHA256withRSA")
        check.initVerify(publicKey)
        file.inputStream().use { input ->
            val buffer = ByteArray(65535)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                check.update(buffer, 0, read)
            }
        }
        return check.verify(signature)
    }

    companion object {
        fun fromPem(stream: InputStream): SignatureVerifier {
            val certificate = CertificateFactory.getInstance("X.509").generateCertificate(stream)
            return SignatureVerifier(certificate.publicKey)
        }
    }
}

/** Reads the "file:base64-signature" lines that the runtime host publishes. */
object RuntimeSignatures {
    fun decodeBundle(text: String): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        for (line in text.lines()) {
            val parts = line.trim().split(":")
            if (parts.size != 2) continue
            val bytes = try {
                java.util.Base64.getMimeDecoder().decode(parts[1])
            } catch (e: IllegalArgumentException) {
                continue
            }
            // An RSA-4096 signature is always 512 bytes.
            if (bytes.size == 512) result[parts[0]] = bytes
        }
        return result
    }
}
KT_EOF

# ---------------------------------------------------------------------------
cat > "$BASE/data/runtime/JavaRuntimeManager.kt" << 'KT_EOF'
package com.voidlauncher.app.data.runtime

import android.content.Context
import android.os.Build
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.voidlauncher.app.data.remote.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/** Java versions this launcher can install. Paths follow the PojavLauncher runtime host. */
enum class JavaRuntimeSpec(val majorVersion: Int, val remotePath: String) {
    JAVA_8(8, "components/jre-new"),
    JAVA_17(17, "components/jre-new"),
    JAVA_21(21, "components/jre-21"),
    JAVA_25(25, "components/jre-25");

    companion object {
        /** The smallest runtime that is at least [required]. Falls back to the newest. */
        fun forMajor(required: Int): JavaRuntimeSpec =
            entries.firstOrNull { it.majorVersion >= required } ?: JAVA_25
    }
}

/**
 * Downloads a Java runtime the first time a version needs it, checks every archive's
 * signature, and unpacks it into filesDir/runtimes/java-<major>.
 * A runtime is only marked ready after its libjvm.so has been found.
 */
class JavaRuntimeManager(private val context: Context) {

    companion object {
        private const val HOST = "https://mojolauncher.github.io/jre-download/"
        private const val MARKER = ".void-runtime-ok"
    }

    private val root = File(context.filesDir, "runtimes")

    fun home(spec: JavaRuntimeSpec): File = File(root, "java-${spec.majorVersion}")

    fun isInstalled(spec: JavaRuntimeSpec): Boolean = File(home(spec), MARKER).isFile

    suspend fun install(requiredMajor: Int, onStage: (String) -> Unit): File = withContext(Dispatchers.IO) {
        val spec = JavaRuntimeSpec.forMajor(requiredMajor)
        val home = home(spec)
        if (isInstalled(spec)) return@withContext home

        onStage("Java ${spec.majorVersion}: checking the signed runtime list")
        val signatures = RuntimeSignatures.decodeBundle(Http.getText("$HOST${spec.remotePath}/version"))
        val arch = deviceArch()

        val universalName = "universal.tar.xz"
        val platformName = "bin-$arch.tar.xz"
        val universalSig = signatures[universalName]
        val platformSig = signatures[platformName]
        if (universalSig == null || platformSig == null) {
            throw IOException("No signed Java ${spec.majorVersion} runtime is published for $arch.")
        }

        val verifier = context.assets.open("runtime-cert.pem").use { SignatureVerifier.fromPem(it) }
        val cache = File(context.cacheDir, "runtime-${spec.majorVersion}").apply { mkdirs() }
        try {
            val universal = fetchVerified(
                "$HOST${spec.remotePath}/$universalName", File(cache, universalName),
                universalSig, verifier, "common files", onStage,
            )
            val platform = fetchVerified(
                "$HOST${spec.remotePath}/$platformName", File(cache, platformName),
                platformSig, verifier, "$arch files", onStage,
            )

            onStage("Java ${spec.majorVersion}: unpacking")
            home.deleteRecursively()
            home.mkdirs()
            extractTarXz(universal, home)
            extractTarXz(platform, home)
        } finally {
            cache.deleteRecursively()
        }

        if (findLibjvm(home) == null) {
            home.deleteRecursively()
            throw IOException("Java ${spec.majorVersion} unpacked without libjvm.so, so it cannot be used.")
        }
        File(home, MARKER).writeText(spec.majorVersion.toString())
        home
    }

    private fun fetchVerified(
        url: String,
        target: File,
        signature: ByteArray,
        verifier: SignatureVerifier,
        label: String,
        onStage: (String) -> Unit,
    ): File {
        onStage("Downloading $label")
        Http.download(url, target, null)
        if (!verifier.verify(target, signature)) {
            target.delete()
            throw IOException("Signature check failed for $label. The runtime was not installed.")
        }
        return target
    }

    private fun deviceArch(): String = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a" -> "arm64"
        "armeabi-v7a" -> "arm"
        "x86_64" -> "x86_64"
        "x86" -> "x86"
        else -> throw IOException("Unsupported CPU: ${Build.SUPPORTED_ABIS.joinToString()}")
    }

    private fun findLibjvm(home: File): File? =
        home.walkTopDown().firstOrNull { it.isFile && it.name == "libjvm.so" }

    private fun extractTarXz(archive: File, dest: File) {
        val destRoot = dest.canonicalPath + File.separator
        FileInputStream(archive).use { fileStream ->
            TarArchiveInputStream(XZCompressorInputStream(BufferedInputStream(fileStream))).use { tar ->
                var entry: TarArchiveEntry? = tar.nextEntry
                while (entry != null) {
                    val target = File(dest, entry.name)
                    if (!target.canonicalPath.startsWith(destRoot)) {
                        throw IOException("Unsafe path in runtime archive: ${entry.name}")
                    }
                    target.parentFile?.mkdirs()
                    when {
                        entry.isSymbolicLink -> {
                            // The link lives at target and points to the stored link name.
                            try {
                                Os.symlink(entry.linkName, target.path)
                            } catch (e: ErrnoException) {
                                if (e.errno != OsConstants.EEXIST) throw IOException(e)
                            }
                        }
                        entry.isDirectory -> target.mkdirs()
                        else -> {
                            FileOutputStream(target).use { out -> tar.copyTo(out) }
                            if ((entry.mode and 0x49) != 0) target.setExecutable(true, false)
                        }
                    }
                    entry = tar.nextEntry
                }
            }
        }
    }
}
KT_EOF

# ---------------------------------------------------------------------------
cat > "$BASE/ui/downloads/DownloadsViewModel.kt" << 'KT_EOF'
package com.voidlauncher.app.ui.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voidlauncher.app.data.SelectedVersion
import com.voidlauncher.app.data.VersionRepository
import com.voidlauncher.app.data.download.GameDownloader
import com.voidlauncher.app.data.local.VersionCache
import com.voidlauncher.app.data.remote.GameMetadataParser
import com.voidlauncher.app.data.runtime.JavaRuntimeManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val minecraftRoot = File(application.filesDir, "minecraft")
    private val downloader = GameDownloader(minecraftRoot)
    private val runtimes = JavaRuntimeManager(application)
    private val versions = VersionRepository(VersionCache(application))

    private val _uiState = MutableStateFlow(DownloadsUiState(selectedId = SelectedVersion.flow.value))
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch {
            SelectedVersion.flow.collect { id ->
                _uiState.update { it.copy(selectedId = id) }
            }
        }
    }

    fun startDownload() {
        val id = _uiState.value.selectedId ?: return
        if (job?.isActive == true) return

        job = viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, progress = null, message = null, isError = false) }
            try {
                val version = versions.loadCached()?.firstOrNull { it.id == id }
                    ?: throw IllegalStateException("Open the Versions tab first so the version list is saved.")

                // 1. Game files (metadata, client, libraries, assets).
                downloader.download(version) { progress ->
                    _uiState.update { it.copy(progress = progress) }
                }

                // 2. The Java runtime this version needs.
                val metadata = GameMetadataParser.parse(
                    File(minecraftRoot, "versions/$id/$id.json").readText(),
                )
                runtimes.install(metadata.javaMajorVersion) { stage ->
                    _uiState.update { it.copy(progress = null, message = stage, isError = false) }
                }

                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        message = "Minecraft $id and Java ${metadata.javaMajorVersion} are ready on this device.",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isDownloading = false, isError = true, message = e.message ?: "Download failed.")
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _uiState.update {
            it.copy(isDownloading = false, message = "Download cancelled. Start again to resume.")
        }
    }
}
KT_EOF

# ---------------------------------------------------------------------------
cat >> THIRD_PARTY.md << 'MD_EOF'

## Java runtime (downloaded on first use)
- **Source:** https://mojolauncher.github.io/jre-download/, the runtime host used by PojavLauncher.
- **Status: license of these runtimes is NOT yet verified.** Confirm it before any public release.
- **Signature certificate:** `app/src/main/assets/runtime-cert.pem`, copied from the MojoLauncher PojavLauncher fork (LGPL-3.0), file `app_pojavlauncher/src/main/assets/cert.pem`. It is used only to check runtime signatures.

## Libraries added in 0.6.0
| Library | Use | License |
|---|---|---|
| Apache Commons Compress 1.27.1 | Reads tar archives | Apache-2.0 |
| XZ for Java 1.10 | Decompresses .xz archives | Public domain (per project) |
MD_EOF

# ---------------------------------------------------------------------------
cat > CHECKLIST.md << 'MD_EOF'
# Void Launcher development checklist

## Milestone 0.1.0 - Foundation (complete)
- [x] Gradle skeleton, CI APK build, theme, navigation, Home screen
- [x] License: GPL-3.0 (`LICENSE`) and third-party notices (`THIRD_PARTY.md`)

## Milestone 0.2.0 - Version manager (complete)
- [x] Mojang version manifest, offline cache, release and snapshot filters

## Milestone 0.3.0 - Landscape UI overhaul (complete)
- [x] Landscape layout, animated backdrop, top tabs, Settings tabs

## Milestone 0.4.0 - Mods (Fabric via Modrinth)
- [x] Search Modrinth for Fabric mods matching the selected version
- [x] Install with SHA-1 check and required dependencies (one level)
- [x] Per-version mods folder, installed list, remove
- [x] Selected version shared between Versions, Home and Mods
- [x] CI build passes
- [ ] Verified on a phone
- [ ] Fabric loader install (needs the engine milestone)
- [ ] Quilt, NeoForge, Forge
- [ ] OptiFine: import a jar you downloaded yourself

## Milestone 0.5.0 - Game files download (complete in code)
- [x] Version metadata, client jar, libraries and assets downloaded with SHA-1 checks
- [x] Existing valid files are skipped, so an interrupted download can resume
- [x] Downloads screen with progress, cancel and error messages
- [x] CI build passes
- [ ] Verified on a phone
- [ ] Native libraries extracted (engine milestone)

## Milestone 0.6.0 - Java runtime (complete in code)
- [x] Runtime chosen from the version's required Java major (8, 17, 21, 25)
- [x] Archives checked with RSA-4096 signatures before unpacking
- [x] Unpacks to filesDir/runtimes/java-<major>, marked ready only if libjvm.so exists
- [ ] Runtime license verified (blocks public release)
- [ ] CI build passes
- [ ] Verified on a phone
- [ ] unpack200 step and libawt dummy (needed before the engine can run)

## Later milestones
- [ ] Settings saved between launches
- [ ] Microsoft login
- [ ] Engine API contracts
- [ ] Pojav adapter (first game launch)
- [ ] Touch controls
- [ ] Logs and diagnostics
MD_EOF

echo
echo "Done. Files written for milestone 0.6.0."
echo "Next:  push and check the GitHub Actions run."
echo "Then:  git add . && git commit -m \"Milestone 0.6.0: Java runtime download\" && git push"

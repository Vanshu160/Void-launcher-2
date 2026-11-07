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

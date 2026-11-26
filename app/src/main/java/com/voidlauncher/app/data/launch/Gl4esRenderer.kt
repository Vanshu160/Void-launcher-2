package com.voidlauncher.app.data.launch

import git.artdeell.mojoexec.MojoExec
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Loads the GL4ES renderer (OpenGL over OpenGL ES) before the Minecraft JVM starts, the same way
 * PojavLauncher's GLESRenderSpec does.
 *
 * MojoExec.prepareEgl loads the library and makes it the EGL and OpenGL provider for GLFW.
 * LWJGL then asks for "libGLMojo.so" (see -Dorg.lwjgl.opengl.libname in GameLauncher), and the
 * dlopen hook from PojavLauncher returns that same renderer handle.
 *
 * Binary: libgl4es_114.so, arm64-v8a, from PojavLauncher at b32a74357b1f064193beebfe1baa12c226568353.
 * GL4ES is MIT licensed. See THIRD_PARTY.md.
 */
object Gl4esRenderer {

    const val LIBRARY_NAME = "libgl4es_114.so"

    /** The GLES version PojavLauncher passes for GL4ES ("opengles2"). */
    private const val GLES_VERSION = 2

    /**
     * Minecraft releases from this date on need OpenGL 3.2 core. GL4ES 1.1.4 targets OpenGL 2.1, so it cannot
     * run them. PojavLauncher uses the same cutoff (GameRunner.isGl4esCompatible, 2025-01-07) and switches to
     * its GLES 3 renderer instead, which this project does not bundle.
     */
    private val GL32_REQUIRED_SINCE: Instant = Instant.parse("2025-01-07T00:00:00Z")

    /**
     * True if Minecraft [releaseTime] (the version JSON's ISO 8601 value) is before the OpenGL 3.2 cutoff, false if
     * it is on or after, and null if the value is missing or cannot be parsed.
     */
    fun supportsVersion(releaseTime: String?): Boolean? {
        if (releaseTime.isNullOrBlank()) return null
        val released = try {
            OffsetDateTime.parse(releaseTime).toInstant()
        } catch (e: DateTimeParseException) {
            return null
        }
        return released.isBefore(GL32_REQUIRED_SINCE)
    }

    /**
     * Set once a renderer loads in this process. The JVM can only start once per process, so
     * a second launch must not load the renderer again.
     */
    @Volatile
    private var prepared = false

    /**
     * Environment variables from PojavLauncher's GLESRenderSpec.setupEnvironment, minus the
     * ANGLE-specific ones. Pojav's native GLES provider sets no further variables.
     */
    fun environment(): Map<String, String> = mapOf(
        "force_glsl_extensions_warn" to "true",
        "allow_higher_compat_version" to "true",
        "allow_glsl_extension_directive_midshader" to "true",
        // Stops OptiFine and other Minecraft error reporting from filling the log.
        "LIBGL_NOERROR" to "1",
    )

    /**
     * Loads the renderer from [nativeLibDir]. Returns true if it loaded, or was already loaded.
     * Must run after System.loadLibrary("mojoexec") and before the JVM starts.
     * Progress and failures go to [log].
     */
    @Synchronized
    fun prepare(nativeLibDir: File, log: (String) -> Unit): Boolean {
        if (prepared) {
            log("GL4ES renderer already loaded in this process")
            return true
        }
        val library = File(nativeLibDir, LIBRARY_NAME)
        if (!library.isFile) {
            log("GL4ES renderer not found: ${library.absolutePath}. The APK's native libraries must be extracted to disk.")
            return false
        }
        MojoExec.setNativeLibraryDir(nativeLibDir.absolutePath)
        // GL4ES reads its LIBGL_* variables when it loads, so the environment must be set before this call.
        log("Loading GL4ES on thread ${Thread.currentThread().name} (id ${Thread.currentThread().id})")
        val ok = MojoExec.prepareEgl(library.absolutePath, false, true, GLES_VERSION)
        if (ok) {
            log("GL4ES renderer loaded from ${library.absolutePath}")
        } else {
            log("GL4ES renderer failed to load from ${library.absolutePath}. EGL/OpenGL will not be available.")
        }
        prepared = ok
        return ok
    }
}

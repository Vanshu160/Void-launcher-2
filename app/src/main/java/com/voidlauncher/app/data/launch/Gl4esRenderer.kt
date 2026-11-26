package com.voidlauncher.app.data.launch

import git.artdeell.mojoexec.MojoExec
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * The OpenGL-over-OpenGL-ES renderers this app bundles. Each is loaded by file path before the Minecraft
 * JVM starts, the same way PojavLauncher's GLESRenderSpec does.
 */
enum class GlRenderer(
    /** Stable id saved in settings. Do not change it once released. */
    val id: String,
    /** Name shown to the player. */
    val label: String,
    /** File in the APK's native library directory. */
    val libraryName: String,
    /** The GLES version passed to MojoExec.prepareEgl, the same value PojavLauncher uses for this renderer. */
    val glesVersion: Int,
    /** What this renderer is known to provide, shown when a version is blocked. */
    val glNote: String,
) {
    /** GL4ES 1.1.4 from PojavLauncher, MIT. The default. */
    GL4ES(
        id = "gl4es",
        label = "GL4ES 1.1.4",
        libraryName = "libgl4es_114.so",
        glesVersion = 2,
        glNote = "OpenGL 2.1",
    ),

    /**
     * NG-GL4ES, MIT, built from pinned source by this project's CMake. Experimental: its OpenGL level is
     * set by LIBGL_GL in [Gl4esRenderer.environment], and it has not been run on a device yet.
     */
    NG_GL4ES(
        id = "ng_gl4es",
        label = "NG-GL4ES",
        libraryName = "libng_gl4es.so",
        glesVersion = 3,
        glNote = "an OpenGL 3.1 target that is not yet verified on a device",
    );

    companion object {
        val DEFAULT: GlRenderer = GL4ES

        /** Unknown or missing saved ids use the default. */
        fun fromId(id: String?): GlRenderer = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * Loads a renderer and checks the Minecraft release gate. Only one renderer can be loaded per process,
 * because the JVM starts once per process and the renderer's environment is read when it loads.
 */
object Gl4esRenderer {

    /**
     * PojavLauncher's GameRunner.isGl4esCompatible calls DateUtils.dateBefore(releaseDate, 2025, 1, 7). That
     * helper takes a zero-based month, so 1 is February: a release is allowed when it is before 7 February 2025.
     * Minecraft releases from that date on need OpenGL 3.2 core. PojavLauncher then switches to its GLES 3
     * renderer, which this project does not bundle. The gate stays for both renderers until a device run shows
     * what NG-GL4ES actually provides.
     */
    private val RELEASE_CUTOFF: LocalDate = LocalDate.of(2025, 2, 7)
    private const val CUTOFF_TEXT = "7 February 2025"

    /**
     * Returns null if [renderer] can run Minecraft [versionId] released at [releaseTime] (the version JSON's
     * ISO 8601 "releaseTime"). Otherwise returns the reason to show the player.
     *
     * A missing or unreadable release date is refused. The cutoff cannot be checked without it, and a version
     * file that lacks a date is not one that Mojang publishes.
     *
     * Only the date part is compared, as PojavLauncher does, so the time zone of the release timestamp does not matter.
     */
    fun blockReason(renderer: GlRenderer, versionId: String, releaseTime: String?): String? {
        if (releaseTime.isNullOrBlank()) {
            return "Minecraft $versionId has no release date in its version file, so ${renderer.label} cannot check it. " +
                "Download the game files again."
        }
        val released = try {
            LocalDate.parse(releaseTime.substringBefore('T'))
        } catch (e: DateTimeParseException) {
            return "Minecraft $versionId has an unreadable release date ($releaseTime), so ${renderer.label} cannot check it. " +
                "Download the game files again."
        }
        if (!released.isBefore(RELEASE_CUTOFF)) {
            return "Minecraft $versionId was released on $released and needs OpenGL 3.2. ${renderer.label} provides " +
                "${renderer.glNote}. Choose a version released before $CUTOFF_TEXT, such as 1.21.4."
        }
        return null
    }

    /** The renderer loaded in this process, or null if none has loaded. */
    @Volatile
    private var loaded: GlRenderer? = null

    /**
     * GL4ES's base environment, from PojavLauncher's GLESRenderSpec.setupEnvironment, minus the ANGLE-specific ones.
     */
    private val GL4ES_ENV: Map<String, String> = mapOf(
        "force_glsl_extensions_warn" to "true",
        "allow_higher_compat_version" to "true",
        "allow_glsl_extension_directive_midshader" to "true",
        // Stops OptiFine and other Minecraft error reporting from filling the log.
        "LIBGL_NOERROR" to "1",
    )

    /**
     * NG-GL4ES's environment, from PojavLauncher's NG-GL4ES render spec. It is exactly these five variables,
     * and it does not include GL4ES's base environment.
     */
    private val NG_GL4ES_ENV: Map<String, String> = mapOf(
        "LIBGL_USE_MC_COLOR" to "1",
        "LIBGL_GL" to "31",
        "LIBGL_ES" to "3",
        "LIBGL_NORMALIZE" to "1",
        "LIBGL_NOERROR" to "1",
    )

    /** The environment variables [renderer] needs. They must be set before its library loads. */
    fun environment(renderer: GlRenderer): Map<String, String> = when (renderer) {
        GlRenderer.GL4ES -> GL4ES_ENV
        GlRenderer.NG_GL4ES -> NG_GL4ES_ENV
    }

    /**
     * Returns the reason [renderer] cannot be used in this process, or null if it can. A different renderer
     * already loaded means the app must restart before the new one can load.
     */
    fun conflictReason(renderer: GlRenderer): String? {
        val current = loaded ?: return null
        if (current == renderer) return null
        return "${current.label} is already loaded in this app session. Restart Void Launcher to use ${renderer.label}."
    }

    /**
     * Loads [renderer] from [nativeLibDir]. Returns true if it loaded, or was already loaded.
     * Must run after System.loadLibrary("mojoexec") and before the JVM starts.
     * Progress and failures go to [log].
     */
    @Synchronized
    fun prepare(nativeLibDir: File, renderer: GlRenderer, log: (String) -> Unit): Boolean {
        conflictReason(renderer)?.let { conflict ->
            log(conflict)
            return false
        }
        if (loaded == renderer) {
            log("${renderer.label} renderer already loaded in this process")
            return true
        }
        val library = File(nativeLibDir, renderer.libraryName)
        if (!library.isFile) {
            log("${renderer.label} renderer not found: ${library.absolutePath}. The APK's native libraries must be extracted to disk.")
            return false
        }
        MojoExec.setNativeLibraryDir(nativeLibDir.absolutePath)
        // The renderer reads its environment variables when it loads, so they must be set before this call.
        log("Loading ${renderer.label} on thread ${Thread.currentThread().name} (id ${Thread.currentThread().id})")
        val ok = MojoExec.prepareEgl(library.absolutePath, false, true, renderer.glesVersion)
        if (ok) {
            log("${renderer.label} renderer loaded from ${library.absolutePath}")
            loaded = renderer
        } else {
            log("${renderer.label} renderer failed to load from ${library.absolutePath}. EGL/OpenGL will not be available.")
        }
        return ok
    }
}

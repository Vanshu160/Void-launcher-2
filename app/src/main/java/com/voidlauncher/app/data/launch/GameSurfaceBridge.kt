package com.voidlauncher.app.data.launch

import android.graphics.SurfaceTexture
import android.view.Surface
import com.voidlauncher.app.data.log.AppLog
import git.artdeell.dnbootstrap.glfw.GLFW
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Carries the Android rendering surface and input from the game screen to GLFW, the same way PojavLauncher's
 * GameView does with Platform.updateSurface and GLFWBackend.
 *
 * Threads:
 * - TextureView callbacks, touch, key and generic motion events run on the UI thread. They never block on
 *   native code. Input is handled in [GameInput] on the UI thread, as PojavLauncher does.
 * - Every GLFW surface call runs on [glfwThread], in order. nativeSurfaceDestroyed does not block (see the
 *   fork patch), but it still runs off the UI thread so the UI never waits on native code.
 * - [awaitFirstSurface] is called from the launch thread, never from the UI thread.
 * - GLFW's cursor callbacks come from the JVM thread. They are passed to [GameInput] on the main thread.
 *
 * Ordering: GLFW is told about a surface only after [markGlfwReady], which the launcher calls after GLFW's
 * native library is initialised. The launch thread starts the JVM only after the first surface has been
 * forwarded to GLFW. Input is sent only while [inputReady] is true, which is only between a successful
 * surface forward and its teardown.
 *
 * Ownership: each surface is a [Surface] over a [SurfaceTexture] owned by this bridge. The texture view is
 * told not to release it (it returns false), and the bridge releases both after GLFW has let go. Each holder is
 * torn down once: either when its texture is destroyed, or when a newer surface replaces it.
 *
 * Stale callbacks: only the holder that is current can affect GLFW. A texture from an earlier Activity instance
 * or an earlier launch is never current, so its callbacks only release its own resources.
 */
object GameSurfaceBridge {

    private val activeState = MutableStateFlow(false)

    /** True from the start of a launch until the game stops. The overlay shows while true. */
    val active: StateFlow<Boolean> = activeState.asStateFlow()

    private val forwardedState = MutableStateFlow(false)

    /** True while GLFW holds the game surface, so the loading screen can be hidden. */
    val surfaceReady: StateFlow<Boolean> = forwardedState.asStateFlow()

    private val statusState = MutableStateFlow("")

    /** The latest launch status, shown on the loading screen until the surface is ready. */
    val status: StateFlow<String> = statusState.asStateFlow()

    /** True while GLFW has a window and input may be sent. Written by the GLFW, UI and launch threads. */
    @Volatile
    var inputReady = false
        private set

    @Volatile
    private var launchReady = CountDownLatch(1)

    @Volatile
    private var cancelRequested = false

    private val glfwThread = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "glfw-surface").apply { isDaemon = true }
    }

    // Only touched on the UI thread.
    private var uiTexture: SurfaceTexture? = null

    // Only touched on glfwThread.
    private var glfwReady = false
    private var forwarded = false
    private var current: Holder? = null

    private class Holder(val surface: Surface, val texture: SurfaceTexture)

    /** Called by the launcher before it waits for a surface. Shows the game screen and routes input to it. */
    fun beginGame() {
        launchReady = CountDownLatch(1)
        cancelRequested = false
        // Called from the launch thread. GameInput's state belongs to the main thread, so the reset is posted there.
        GameInput.resetFromAnyThread()
        GLFW.setGrabListener { isGrabbing -> GameInput.onGameGrabState(isGrabbing) }
        GLFW.setPositionCallback { x, y -> GameInput.onGameCursorPos(x, y) }
        activeState.value = true
    }

    /** Called by the launcher to show a step of the launch on the loading screen. Any thread. */
    fun reportStatus(text: String) {
        statusState.value = text
    }

    /**
     * Called from the loading screen's Cancel button (UI thread). Stops the launch before the JVM starts.
     * The launch thread then ends the game screen. Has no effect once Minecraft is running.
     */
    fun cancelLaunch() {
        cancelRequested = true
        launchReady.countDown()
    }

    /** True if the user cancelled the launch while the loading screen was shown. */
    fun isLaunchCancelled(): Boolean = cancelRequested

    /** Called by the launcher after GLFW's native library is initialised. Forwards a surface that arrived earlier. */
    fun markGlfwReady() {
        glfwThread.execute {
            glfwReady = true
            forwardIfPossible()
        }
    }

    /**
     * Blocks the calling (non-UI) thread until a surface has been forwarded to GLFW for this launch, or until
     * the launch is cancelled. Returns false if [timeoutMs] passes first.
     */
    fun awaitFirstSurface(timeoutMs: Long): Boolean = launchReady.await(timeoutMs, TimeUnit.MILLISECONDS)

    /** Called on the UI thread when a TextureView surface becomes available. Width and height are the buffer size. */
    fun onSurfaceCreated(texture: SurfaceTexture, width: Int, height: Int) {
        uiTexture = texture
        GameInput.setWindowSize(width, height)
        val holder = Holder(Surface(texture), texture)
        glfwThread.execute {
            // A surface that was never destroyed is torn down before the new one is used. Its texture is released
            // here, and its later destroy callback is ignored.
            current?.let { teardown(it) }
            current = holder
            forwardIfPossible()
        }
    }

    /** Called on the UI thread when the surface size changes. */
    fun onSurfaceUpdated(width: Int, height: Int) {
        GameInput.setWindowSize(width, height)
        glfwThread.execute {
            if (forwarded && glfwReady) GLFW.nativeSurfaceUpdated()
        }
    }

    /**
     * Called on the UI thread when the texture view releases its surface. The texture view must not
     * release the texture itself; this bridge does, after GLFW has let go of the window.
     */
    fun onSurfaceDestroyed(texture: SurfaceTexture) {
        // Only the surface that is current on the UI side may stop input. A stale callback for an earlier
        // texture must not stop input for the surface that replaced it.
        if (uiTexture === texture) {
            uiTexture = null
            // Release held buttons while GLFW can still receive them, then stop routing input. The grab state is
            // kept: Minecraft still holds the cursor across a surface change, such as rotation.
            GameInput.releaseHeld()
            inputReady = false
        }
        glfwThread.execute {
            val holder = current
            // A texture that is not the current one was already torn down when it was replaced in onSurfaceCreated.
            // Releasing it again here would be a second release, so nothing is done.
            if (holder != null && holder.texture === texture) {
                current = null
                teardown(holder)
            }
        }
    }

    /**
     * Called when the game stops, from any thread. Input stops first, so nothing is sent to a game that has
     * exited. The surface is still released by [onSurfaceDestroyed] when the overlay goes away: nativeSurfaceDestroyed
     * does not block, so the teardown can always run.
     */
    fun endGame() {
        inputReady = false
        glfwThread.execute { glfwReady = false }
        activeState.value = false
    }

    // Runs on glfwThread only.
    private fun forwardIfPossible() {
        val holder = current ?: return
        if (!glfwReady || forwarded) return
        GLFW.nativeSurfaceCreated(holder.surface)
        forwarded = true
        inputReady = true
        forwardedState.value = true
        AppLog.write("Rendering surface forwarded to GLFW.")
        launchReady.countDown()
    }

    // Runs on glfwThread only.
    private fun teardown(holder: Holder) {
        if (forwarded) {
            forwarded = false
            inputReady = false
            forwardedState.value = false
            AppLog.write("Releasing rendering surface from GLFW.")
            // Safe after the game has stopped: the native side does not wait for the render thread.
            GLFW.nativeSurfaceDestroyed()
        }
        holder.surface.release()
        holder.texture.release()
    }
}

package com.voidlauncher.app.data.launch

import git.artdeell.dnbootstrap.glfw.GLFW

/**
 * Sends key and mouse button presses to GLFW, and tracks which owner holds each one, so a code held by two owners
 * is pressed once and released once. Owners are the touch controls, the finger gestures in [GameInput] and taps.
 *
 * Nothing is sent while GLFW input is not ready. The bookkeeping still runs, so a later release is never lost.
 * Main thread only.
 */
object HeldInputs {
    private const val GLFW_PRESS = 1
    private const val GLFW_RELEASE = 0

    private val keys = HeldCodes()
    private val buttons = HeldCodes()

    /** Presses or lets go of a GLFW key for [owner]. */
    fun setKey(owner: Any, glfwKey: Int, down: Boolean) {
        val pressed = keys.set(owner, glfwKey, down) ?: return
        send { GLFW.sendKeyEvent(glfwKey, if (pressed) GLFW_PRESS else GLFW_RELEASE, 0) }
    }

    /** Presses or lets go of a GLFW mouse button for [owner]. */
    fun setButton(owner: Any, button: Int, down: Boolean) {
        val pressed = buttons.set(owner, button, down) ?: return
        send { GLFW.sendMouseEvent(button, if (pressed) GLFW_PRESS else GLFW_RELEASE, 0) }
    }

    /** A key press and release at once, such as Inventory or Chat. */
    fun tapKey(owner: Any, glfwKey: Int) {
        setKey(owner, glfwKey, true)
        setKey(owner, glfwKey, false)
    }

    /** Lets go of everything [owner] holds. */
    fun releaseOwner(owner: Any) {
        for (key in keys.releaseOwner(owner)) send { GLFW.sendKeyEvent(key, GLFW_RELEASE, 0) }
        for (button in buttons.releaseOwner(owner)) send { GLFW.sendMouseEvent(button, GLFW_RELEASE, 0) }
    }

    /** Lets go of every key and button, whoever holds it. */
    fun releaseAll() {
        for (key in keys.releaseAll()) send { GLFW.sendKeyEvent(key, GLFW_RELEASE, 0) }
        for (button in buttons.releaseAll()) send { GLFW.sendMouseEvent(button, GLFW_RELEASE, 0) }
    }

    private inline fun send(block: () -> Unit) {
        if (GameSurfaceBridge.inputReady) block()
    }
}

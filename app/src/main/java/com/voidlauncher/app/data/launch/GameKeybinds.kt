package com.voidlauncher.app.data.launch

import android.view.KeyEvent
import git.artdeell.dnbootstrap.glfw.GLFW

/**
 * An action the player can bind to a key. [glfwKey] is the GLFW code Minecraft listens for, and
 * [defaultKeyCode] is the Android key code used until the player changes it.
 */
enum class GameAction(val label: String, val defaultKeyCode: Int, val glfwKey: Int) {
    FORWARD("Move forward", KeyEvent.KEYCODE_W, 87),
    BACKWARD("Move backward", KeyEvent.KEYCODE_S, 83),
    LEFT("Strafe left", KeyEvent.KEYCODE_A, 65),
    RIGHT("Strafe right", KeyEvent.KEYCODE_D, 68),
    JUMP("Jump", KeyEvent.KEYCODE_SPACE, 32),
    SNEAK("Sneak", KeyEvent.KEYCODE_SHIFT_LEFT, 340),
    SPRINT("Sprint", KeyEvent.KEYCODE_CTRL_LEFT, 341),
    INVENTORY("Inventory", KeyEvent.KEYCODE_E, 69),
    DROP("Drop item", KeyEvent.KEYCODE_Q, 81),
    CHAT("Chat", KeyEvent.KEYCODE_T, 84),
}

/**
 * Routes hardware key presses during a game. A key bound to an action sends that action's GLFW key.
 * A key that was a default binding and has been moved only types its character, so it does not
 * trigger the old action. Any other key is passed to GLFW as it is.
 *
 * The bindings are pushed in by [update], from SettingsViewModel. Routing runs on the main thread.
 */
object GameKeybinds {
    private const val GLFW_PRESS = 1
    private const val GLFW_RELEASE = 0

    /** Keys the system or the launcher uses, so they cannot be bound. */
    val reservedKeys: Set<Int> = setOf(
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_APP_SWITCH,
    )

    @Volatile
    private var actionByKey: Map<Int, GameAction> = emptyMap()

    /** Default key codes whose action has moved to another key. */
    @Volatile
    private var movedDefaults: Set<Int> = emptySet()

    /** The action each held key started, so its release matches its press even after a rebind. Main thread only. */
    private val heldActions = HashMap<Int, GameAction>()

    fun update(bindings: Map<GameAction, Int>) {
        actionByKey = bindings.entries.associate { (action, key) -> key to action }
        movedDefaults = GameAction.entries
            .filter { (bindings[it] ?: it.defaultKeyCode) != it.defaultKeyCode }
            .map { it.defaultKeyCode }
            .filter { it !in actionByKey }
            .toSet()
    }

    /** A readable name for a key, such as "W" or "SHIFT LEFT". */
    fun keyName(keyCode: Int): String =
        KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_").replace('_', ' ')

    /** Sends one key press or release to GLFW. Only call this while GLFW input is ready. */
    fun route(androidCode: Int, pressed: Boolean, mods: Int, codepoint: Char) {
        val hasText = codepoint != 0.toChar()
        if (pressed) {
            val action = actionByKey[androidCode]
            when {
                action != null -> {
                    heldActions[androidCode] = action
                    GLFW.sendKeyEvent(action.glfwKey, GLFW_PRESS, mods)
                    // Text still reaches chat and signs, the same as on a desktop keyboard.
                    if (hasText) GLFW.sendBulkUnicodeEvent(codepoint.toString(), mods)
                }
                androidCode in movedDefaults -> {
                    if (hasText) GLFW.sendBulkUnicodeEvent(codepoint.toString(), mods)
                }
                else -> GLFW.sendRawKeyEvent(androidCode, GLFW_PRESS, mods, codepoint)
            }
        } else {
            val action = heldActions.remove(androidCode)
            when {
                action != null -> GLFW.sendKeyEvent(action.glfwKey, GLFW_RELEASE, mods)
                androidCode in movedDefaults -> Unit
                else -> GLFW.sendRawKeyEvent(androidCode, GLFW_RELEASE, mods, 0.toChar())
            }
        }
    }
}

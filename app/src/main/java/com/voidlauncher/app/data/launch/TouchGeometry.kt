package com.voidlauncher.app.data.launch

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A control's centre as fractions of the screen width and height, measured from the top left. */
typealias ScreenPosition = Pair<Float, Float>

private const val MOUSE_LEFT = 0
private const val MOUSE_RIGHT = 1
private const val KEY_ESCAPE = 256

/** What a touch control does while a finger is on it. */
sealed class ControlAction {
    /** Moves with W, A, S and D while the finger is down. Diagonals hold two keys. */
    object Joystick : ControlAction()

    /** Holds a key while the finger is down. */
    class HoldKey(val key: Int) : ControlAction()

    /** Holds a mouse button while the finger is down. */
    class HoldMouse(val button: Int) : ControlAction()

    /** Presses and releases a key as the finger goes down. */
    class TapKey(val key: Int) : ControlAction()
}

/** Which game context a control is shown in. Controls marked [ALWAYS] are also shown in menus. */
enum class ControlVisibility { WORLD, ALWAYS }

/**
 * The on-screen controls. Each has a default position for every screen shape, which the player can move in the
 * layout editor. The GLFW key and button codes match Minecraft's, the same as the keyboard bindings in [GameAction].
 */
enum class TouchControl(
    val label: String,
    val visibility: ControlVisibility,
    val action: ControlAction,
    val defaultX: Float,
    val defaultY: Float,
) {
    JOYSTICK("Move", ControlVisibility.WORLD, ControlAction.Joystick, 0.14f, 0.70f),
    SPRINT("Sprint", ControlVisibility.WORLD, ControlAction.HoldKey(GameAction.SPRINT.glfwKey), 0.36f, 0.90f),
    ATTACK("Attack", ControlVisibility.WORLD, ControlAction.HoldMouse(MOUSE_LEFT), 0.78f, 0.70f),
    USE("Use", ControlVisibility.WORLD, ControlAction.HoldMouse(MOUSE_RIGHT), 0.92f, 0.52f),
    JUMP("Jump", ControlVisibility.WORLD, ControlAction.HoldKey(GameAction.JUMP.glfwKey), 0.92f, 0.84f),
    SNEAK("Sneak", ControlVisibility.WORLD, ControlAction.HoldKey(GameAction.SNEAK.glfwKey), 0.78f, 0.88f),
    INVENTORY("Inventory", ControlVisibility.WORLD, ControlAction.TapKey(GameAction.INVENTORY.glfwKey), 0.66f, 0.14f),
    DROP("Drop", ControlVisibility.WORLD, ControlAction.TapKey(GameAction.DROP.glfwKey), 0.79f, 0.14f),
    CHAT("Chat", ControlVisibility.WORLD, ControlAction.TapKey(GameAction.CHAT.glfwKey), 0.92f, 0.14f),
    ESCAPE("Esc", ControlVisibility.ALWAYS, ControlAction.TapKey(KEY_ESCAPE), 0.07f, 0.14f),
    ;

    /** True if this control is drawn in the current context: [inWorld] is true while Minecraft holds the cursor. */
    fun isVisible(inWorld: Boolean): Boolean = inWorld || visibility == ControlVisibility.ALWAYS
}

/** Geometry, key mapping and layout parsing for the touch controls. No Android dependency, so it can be unit tested. */
object TouchGeometry {
    /** Button diameter as a fraction of the shorter screen side, at scale 1. */
    private const val BUTTON_FRACTION = 0.14f

    /** Joystick base diameter, in the same units. */
    private const val JOYSTICK_FRACTION = 0.36f

    /** Taps within this multiple of the radius still hit the control. */
    private const val HIT_SLOP = 1.15f

    /** A joystick deflection smaller than this fraction of its radius sends no movement. */
    const val JOYSTICK_DEAD_ZONE = 0.3f

    /** Positions are kept this far from the screen edge, so no control can be placed off screen. */
    const val MIN_POSITION = 0.03f
    const val MAX_POSITION = 0.97f

    /** The keys the joystick holds: forward, backward, left and right, using the default bindings' GLFW codes. */
    val JOYSTICK_KEYS: List<Int> = listOf(
        GameAction.FORWARD.glfwKey,
        GameAction.BACKWARD.glfwKey,
        GameAction.LEFT.glfwKey,
        GameAction.RIGHT.glfwKey,
    )

    /** Radius in pixels of [control] on a screen of [width] by [height] pixels. */
    fun radiusPx(control: TouchControl, scale: Float, width: Int, height: Int): Float {
        val fraction = if (control.action == ControlAction.Joystick) JOYSTICK_FRACTION else BUTTON_FRACTION
        return fraction * scale * min(width, height) / 2f
    }

    /** True if the touch at ([x], [y]) pixels lands on [control], whose centre is at the fractions ([fx], [fy]). */
    fun hit(
        control: TouchControl,
        fx: Float,
        fy: Float,
        scale: Float,
        width: Int,
        height: Int,
        x: Float,
        y: Float,
    ): Boolean {
        val radius = radiusPx(control, scale, width, height) * HIT_SLOP
        val dx = x - fx * width
        val dy = y - fy * height
        return dx * dx + dy * dy <= radius * radius
    }

    /**
     * The movement keys a joystick deflection holds. [dx] and [dy] are the deflection from the centre, in units of
     * the radius, with y growing downwards. A deflection past the dead zone on two axes holds two keys.
     */
    fun joystickKeys(dx: Float, dy: Float): Set<Int> {
        val keys = HashSet<Int>(4)
        if (dy < -JOYSTICK_DEAD_ZONE) keys += GameAction.FORWARD.glfwKey
        if (dy > JOYSTICK_DEAD_ZONE) keys += GameAction.BACKWARD.glfwKey
        if (dx < -JOYSTICK_DEAD_ZONE) keys += GameAction.LEFT.glfwKey
        if (dx > JOYSTICK_DEAD_ZONE) keys += GameAction.RIGHT.glfwKey
        return keys
    }

    /**
     * The layout key for a screen shape: orientation and the aspect ratio to one decimal place, such as "land-2.2".
     * Phones of the same shape share a layout. A screen with no size has its own name.
     */
    fun profileFor(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return "unknown"
        val orientation = if (width >= height) "land" else "port"
        val ratio = (max(width, height).toFloat() / min(width, height) * 10f).roundToInt() / 10f
        return "$orientation-$ratio"
    }

    fun clampPosition(value: Float): Float = value.coerceIn(MIN_POSITION, MAX_POSITION)

    /** Reads a saved "x,y" position. Returns null if it is missing or malformed, and clamps numbers on screen. */
    fun parsePosition(text: String?): ScreenPosition? {
        val parts = text?.split(',') ?: return null
        if (parts.size != 2) return null
        val x = parts[0].toFloatOrNull() ?: return null
        val y = parts[1].toFloatOrNull() ?: return null
        if (!x.isFinite() || !y.isFinite()) return null
        return clampPosition(x) to clampPosition(y)
    }

    fun formatPosition(position: ScreenPosition): String = "${position.first},${position.second}"
}

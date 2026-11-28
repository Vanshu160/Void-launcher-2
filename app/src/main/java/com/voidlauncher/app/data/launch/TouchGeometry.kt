package com.voidlauncher.app.data.launch

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A point as fractions of the screen, or as pixels where the text says so. */
typealias ScreenPosition = Pair<Float, Float>

/** Where a button is held in the layout: the edges it hangs from, and its offsets from them, as fractions. */
data class Anchor(val right: Boolean, val bottom: Boolean, val x: Float, val y: Float)

/**
 * Geometry, key mapping and layout parsing for the touch controls. No Android dependency, so it can be unit tested.
 */
object TouchGeometry {
    /** Button diameter as a fraction of the shorter screen side, at scale 1 and size 1. */
    const val BUTTON_FRACTION = 0.14f

    /** Joystick base diameter, in the same units. */
    const val JOYSTICK_FRACTION = 0.36f

    /** Taps within this multiple of the radius still hit the button. */
    private const val HIT_SLOP = 1.15f

    /** A joystick deflection smaller than this fraction of its radius sends no movement. */
    const val JOYSTICK_DEAD_ZONE = 0.3f

    /** The knob pulled this many radii past the base edge locks forward. */
    const val FORWARD_LOCK_PULL = 0.5f

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

    /** Radius in pixels of [button] on a screen of [width] by [height] pixels. */
    fun radiusPx(button: TouchButton, scale: Float, width: Int, height: Int): Float {
        val fraction = if (button.isJoystick) JOYSTICK_FRACTION else BUTTON_FRACTION
        return fraction * scale * button.size * min(width, height) / 2f
    }

    /** The centre of [button] as fractions of the screen, measured from the top left. */
    fun centreFraction(button: TouchButton): ScreenPosition {
        val fx = if (button.anchorRight) 1f - button.x else button.x
        val fy = if (button.anchorBottom) 1f - button.y else button.y
        return fx to fy
    }

    /** The centre of [button] in pixels. */
    fun centrePx(button: TouchButton, width: Int, height: Int): ScreenPosition {
        val (fx, fy) = centreFraction(button)
        return (fx * width) to (fy * height)
    }

    /**
     * The anchor for a centre at the fractions ([centreX], [centreY]). A centre in the right half hangs from the right
     * edge, and one in the bottom half from the bottom edge, so it keeps its place when the screen changes shape.
     */
    fun anchorFor(centreX: Float, centreY: Float): Anchor {
        val right = centreX > 0.5f
        val bottom = centreY > 0.5f
        val x = clampPosition(if (right) 1f - centreX else centreX)
        val y = clampPosition(if (bottom) 1f - centreY else centreY)
        return Anchor(right, bottom, x, y)
    }

    /**
     * True if the touch at ([x], [y]) pixels lands on [button], whose centre is at [centre] in pixels. The centre
     * defaults to where the layout puts it.
     */
    fun hit(
        button: TouchButton,
        scale: Float,
        width: Int,
        height: Int,
        x: Float,
        y: Float,
        centre: ScreenPosition = centrePx(button, width, height),
    ): Boolean {
        val radius = radiusPx(button, scale, width, height) * HIT_SLOP
        val dx = x - centre.first
        val dy = y - centre.second
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

    /** Moves a deflection that reaches past the base edge back onto the edge, keeping its direction. */
    fun clampToBase(dx: Float, dy: Float): ScreenPosition {
        val length = sqrt(dx * dx + dy * dy)
        return if (length > 1f) (dx / length) to (dy / length) else dx to dy
    }

    /** True when the raw upward deflection [dy] (in radii, unclamped) is far enough to lock forward. */
    fun forwardLockPulled(dy: Float): Boolean = dy < -(1f + FORWARD_LOCK_PULL)

    /** True when the raw deflection [dy] has come back inside the base, which releases a forward lock. */
    fun forwardLockReleased(dy: Float): Boolean = dy > -1f

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

    /**
     * Reads a saved "x,y" position from the older layout format. Returns null if it is missing or malformed, and
     * clamps numbers on screen.
     */
    fun parsePosition(text: String?): ScreenPosition? {
        val parts = text?.split(',') ?: return null
        if (parts.size != 2) return null
        val x = parts[0].toFloatOrNull() ?: return null
        val y = parts[1].toFloatOrNull() ?: return null
        if (!x.isFinite() || !y.isFinite()) return null
        return clampPosition(x) to clampPosition(y)
    }
}

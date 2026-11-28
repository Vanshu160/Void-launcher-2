package com.voidlauncher.app.data.launch

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import git.artdeell.dnbootstrap.glfw.GLFW
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One change to a finger on the game surface, as [TouchRouter] passes it on. */
enum class PointerAction { DOWN, MOVE, UP }

/**
 * Forwards Android touch, mouse, stylus and keyboard input to GLFW, following PojavLauncher's GameView,
 * InGameEventProcessor, LeftClickGesture, RightClickGesture and GLFWBackend.
 *
 * [TouchRouter] decides which finger is camera and cursor input and which is an on-screen control, then calls
 * [onPointer] for each camera finger, with its pointer id. Several fingers can be on the surface at once.
 *
 * Touch model (one primary finger, one optional second finger):
 * - A finger press sends a left button press. Lifting it sends the release, so holding a finger mines or attacks.
 * - Cursor free (menus, absolute mode): the cursor moves to the finger before the press.
 * - Touchpad mode, or cursor grabbed (in the world): movement is relative, scaled by the sensitivity setting.
 * - Long press without movement (gestures enabled): a right-button click. Left stays held, so mining continues.
 * - Two fingers: the first finger's left press is released when the second finger lands. Both fingers
 *   lifting without moving is a right-button click. Dragging vertically scrolls.
 * - After a two-finger gesture, the remaining finger is ignored until every finger is up.
 * - A third finger is ignored. Only the primary finger drives the cursor.
 *
 * Mouse and stylus pointers: movement comes from the touch and hover paths. Buttons come only from
 * generic motion events (ACTION_BUTTON_PRESS and RELEASE), so one click is never sent twice.
 *
 * Keys are sent synchronously, because Android needs the handled result at once. Back sends Escape, as
 * PojavLauncher does, unless the on-screen keyboard is open, in which case Back closes the keyboard.
 *
 * Mouse buttons pressed by fingers are held through [HeldInputs] under [FINGER], so a button an on-screen control
 * also holds (attack or use) is not released early.
 *
 * Threading: everything here runs on the Android main thread. Cursor callbacks from the JVM thread are posted
 * to the main thread. GLFW input is sent only while [GameSurfaceBridge.inputReady] is true.
 */
object GameInput {
    private const val GLFW_PRESS = 1
    private const val GLFW_RELEASE = 0

    private const val BUTTON_LEFT = 0
    private const val BUTTON_RIGHT = 1
    private const val BUTTON_MIDDLE = 2
    private const val BUTTON_4 = 3
    private const val BUTTON_5 = 4

    private const val MOD_SHIFT = 0x0001
    private const val MOD_CONTROL = 0x0002
    private const val MOD_ALT = 0x0004
    private const val MOD_SUPER = 0x0008
    private const val MOD_CAPS_LOCK = 0x0010
    private const val MOD_NUM_LOCK = 0x0020

    /** Long press time before a right-click is sent. PojavLauncher uses a similar delay. */
    private const val LONG_PRESS_MS = 450L

    /** Two fingers lifting within this time, without moving, count as a right-click. */
    private const val TWO_FINGER_TAP_MS = 300L

    /** Movement below this many pixels is not counted as a drag. */
    private const val MOVE_SLOP_PX = 16f

    /** Pixels of two-finger drag per scroll tick. */
    private const val SCROLL_PX_PER_TICK = 40f

    private const val NO_POINTER = -1

    /** The owner of the mouse buttons that camera fingers hold. */
    private val FINGER = Any()

    private val mainHandler = Handler(Looper.getMainLooper())

    private val inWorldState = MutableStateFlow(false)

    /** True while Minecraft holds the cursor, which is when the in-world touch controls are shown. */
    val inWorld: StateFlow<Boolean> = inWorldState.asStateFlow()

    // Main thread only.
    private var windowWidth = 0
    private var windowHeight = 0
    private var cursorX = 0.0
    private var cursorY = 0.0
    private var grabbed = false

    private var primaryId = NO_POINTER
    private var primaryIsMouse = false
    private var primaryDownX = 0f
    private var primaryDownY = 0f
    private var primaryX = 0f
    private var primaryY = 0f
    private var primaryMoved = false
    private var leftHeld = false
    private var rightHeld = false

    // Last primary-finger position, used for relative movement.
    private var lastPrimaryX = 0f
    private var lastPrimaryY = 0f

    private var secondaryId = NO_POINTER
    private var secondaryX = 0f
    private var secondaryY = 0f
    private var secondaryDownX = 0f
    private var secondaryDownY = 0f
    private var twoFingerStartAt = 0L
    private var twoFingerMoved = false
    private var twoFingerLastCentroidY = 0f
    private var scrollRemainder = 0f

    /** Set after a two-finger gesture ends while a finger is still down. Cleared when every finger is up. */
    private var ignoreUntilUp = false

    private var hoverX = 0f
    private var hoverY = 0f

    private val longPressRunnable = Runnable { onLongPress() }

    /** Clears per-game input state from any thread. Runs on the main thread, which owns this state. */
    fun resetFromAnyThread() {
        mainHandler.post { reset() }
    }

    /**
     * Clears per-game input state, including every held key and button bookkeeping and the touch routing. Keys are
     * not sent, so call [releaseHeld] first if the game is still running and they must be released.
     */
    fun reset() {
        mainHandler.removeCallbacks(longPressRunnable)
        HeldInputs.releaseAll()
        TouchRouter.reset()
        primaryId = NO_POINTER
        primaryIsMouse = false
        primaryMoved = false
        leftHeld = false
        rightHeld = false
        secondaryId = NO_POINTER
        twoFingerMoved = false
        scrollRemainder = 0f
        ignoreUntilUp = false
        grabbed = false
        inWorldState.value = false
    }

    /** Called on the main thread when the game surface size is known. */
    fun setWindowSize(width: Int, height: Int) {
        windowWidth = width
        windowHeight = height
        clampCursor()
    }

    /**
     * Called from the JVM thread when Minecraft grabs or releases the cursor. A held button is released
     * first, so Minecraft never sees a press from the old mode without its release. The touch controls are then
     * told the context changed, so they stop holding keys that belong to the old mode.
     */
    fun onGameGrabState(isGrabbing: Boolean) {
        mainHandler.post {
            if (isGrabbing == grabbed) return@post
            releaseHeld()
            grabbed = isGrabbing
            inWorldState.value = isGrabbing
            TouchRouter.onContextChanged()
        }
    }

    /** Called from the JVM thread when Minecraft moves the cursor itself. */
    fun onGameCursorPos(x: Double, y: Double) {
        mainHandler.post {
            cursorX = x
            cursorY = y
            clampCursor()
        }
    }

    /**
     * Releases every button the fingers are holding and cancels any gesture in progress. Sends nothing
     * unless GLFW input is ready, so it is safe to call during teardown. Main thread only.
     */
    fun releaseHeld() {
        mainHandler.removeCallbacks(longPressRunnable)
        HeldInputs.releaseOwner(FINGER)
        leftHeld = false
        rightHeld = false
        primaryId = NO_POINTER
        secondaryId = NO_POINTER
        primaryMoved = false
        twoFingerMoved = false
        ignoreUntilUp = false
    }

    /** Called when no finger is on the game surface, so the gesture state starts again. */
    fun onAllPointersUp() {
        ignoreUntilUp = false
        primaryId = NO_POINTER
        secondaryId = NO_POINTER
    }

    /** Handles one camera finger, as [TouchRouter] routes it. Main thread only. */
    fun onPointer(action: PointerAction, id: Int, x: Float, y: Float, isMouse: Boolean, eventTime: Long) {
        if (!GameSurfaceBridge.inputReady) return
        when (action) {
            PointerAction.DOWN -> pointerDown(id, x, y, isMouse, eventTime)
            PointerAction.MOVE -> pointerMove(id, x, y)
            PointerAction.UP -> pointerUp(id, x, y, eventTime)
        }
    }

    /** Handles hover, scroll and mouse button events. Returns true if the event was consumed. */
    fun onGenericMotion(event: MotionEvent): Boolean {
        if (!GameSurfaceBridge.inputReady) return false
        val mods = modsFrom(event.metaState)
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER -> {
                // Records the start point so the first relative movement is not a jump.
                hoverX = event.getX(0)
                hoverY = event.getY(0)
            }
            MotionEvent.ACTION_HOVER_MOVE -> {
                val x = event.getX(0)
                val y = event.getY(0)
                if (grabbed) {
                    addRelative((x - hoverX).toDouble(), (y - hoverY).toDouble())
                } else {
                    moveCursor(x, y)
                }
                hoverX = x
                hoverY = y
            }
            MotionEvent.ACTION_SCROLL -> GLFW.sendScrollEvent(
                event.getAxisValue(MotionEvent.AXIS_HSCROLL).toDouble(),
                event.getAxisValue(MotionEvent.AXIS_VSCROLL).toDouble(),
            )
            MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE -> {
                val button = glfwButton(event.actionButton)
                if (button < 0) return false
                val state = if (event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS) GLFW_PRESS else GLFW_RELEASE
                GLFW.sendMouseEvent(button, state, mods)
            }
            else -> return false
        }
        return true
    }

    /**
     * Handles a key event. Returns true if it was consumed. While the game screen is shown every key is
     * consumed, except the volume keys, so the launcher does not react to them.
     */
    fun onKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return true
        // Ignore cancelled up events, which happen when the user switches layouts.
        if (event.action == KeyEvent.ACTION_UP && (event.flags and KeyEvent.FLAG_CANCELED) != 0) return true
        if (event.repeatCount != 0) return true

        if (!GameSurfaceBridge.inputReady) {
            // Still loading: Back is the way out, as the Cancel button is. Once the game is running the surface
            // may be briefly gone, and Back must not cancel anything then.
            if (code == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && !GameSurfaceBridge.surfaceReady.value) {
                GameSurfaceBridge.cancelLaunch()
            }
            return true
        }

        if (code == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) {
                // With the keyboard open, Back closes it. Otherwise Back opens Minecraft's pause menu.
                if (GameTextInput.hideIfShown()) return true
                GLFW.sendRawKeyEvent(KeyEvent.KEYCODE_ESCAPE, GLFW_PRESS, 0, 0.toChar())
                GLFW.sendRawKeyEvent(KeyEvent.KEYCODE_ESCAPE, GLFW_RELEASE, 0, 0.toChar())
            }
            return true
        }

        val pressed = event.action == KeyEvent.ACTION_DOWN
        // Control characters (newline, tab, backspace) are not text. Their keys are sent as key events above.
        val unicode = event.getUnicodeChar(event.metaState) and 0xFFFF
        val codepoint = if (pressed && unicode >= 0x20) unicode.toChar() else 0.toChar()
        // Bound keys send their action's GLFW key, and other keys go straight to GLFW. See GameKeybinds.
        GameKeybinds.route(code, pressed, modsFrom(event.metaState), codepoint)
        return true
    }

    // ---- Touch -------------------------------------------------------------------------------------

    private fun pointerDown(id: Int, x: Float, y: Float, isMouse: Boolean, eventTime: Long) {
        if (ignoreUntilUp) return

        if (primaryId == NO_POINTER) {
            primaryId = id
            primaryIsMouse = isMouse
            primaryDownX = x
            primaryDownY = y
            primaryX = x
            primaryY = y
            lastPrimaryX = x
            lastPrimaryY = y
            primaryMoved = false
            if (primaryIsMouse) {
                // A mouse's primary button arrives through generic motion events, so the touch path
                // only positions the cursor here.
                if (!grabbed) moveCursor(x, y)
                return
            }
            if (!grabbed && !touchpadMode()) moveCursor(x, y)
            press(BUTTON_LEFT)
            if (!gesturesDisabled()) {
                mainHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
            }
            return
        }

        if (secondaryId == NO_POINTER && !primaryIsMouse && id != primaryId) {
            // Second finger. Release the first finger's left button so holding it does not continue.
            mainHandler.removeCallbacks(longPressRunnable)
            secondaryId = id
            secondaryX = x
            secondaryY = y
            secondaryDownX = x
            secondaryDownY = y
            twoFingerStartAt = eventTime
            twoFingerMoved = false
            scrollRemainder = 0f
            twoFingerLastCentroidY = (primaryY + secondaryY) / 2f
            if (leftHeld) press(BUTTON_LEFT, pressed = false)
        }
        // Any other finger is ignored.
    }

    private fun pointerMove(id: Int, x: Float, y: Float) {
        if (secondaryId != NO_POINTER && (id == primaryId || id == secondaryId)) {
            if (id == primaryId) {
                primaryX = x
                primaryY = y
            } else {
                secondaryX = x
                secondaryY = y
            }
            twoFingerMove()
            return
        }
        if (id != primaryId || ignoreUntilUp) return

        primaryX = x
        primaryY = y
        if (!primaryMoved && distance(x, y, primaryDownX, primaryDownY) > MOVE_SLOP_PX) {
            primaryMoved = true
            mainHandler.removeCallbacks(longPressRunnable)
        }
        // A mouse moves the cursor absolutely unless the cursor is grabbed. A finger is relative in touchpad mode too.
        val relative = if (primaryIsMouse) grabbed else (grabbed || touchpadMode())
        if (relative) addRelative((x - lastPrimaryX).toDouble(), (y - lastPrimaryY).toDouble())
        else moveCursor(x, y)
        lastPrimaryX = x
        lastPrimaryY = y
    }

    private fun pointerUp(id: Int, x: Float, y: Float, eventTime: Long) {
        if (secondaryId != NO_POINTER && (id == primaryId || id == secondaryId)) {
            endTwoFingerGesture(eventTime)
            // Whichever finger remains is ignored until every finger is up.
            ignoreUntilUp = true
            if (id == primaryId) primaryId = NO_POINTER
            secondaryId = NO_POINTER
            return
        }
        if (id != primaryId) return

        mainHandler.removeCallbacks(longPressRunnable)
        primaryId = NO_POINTER
        if (ignoreUntilUp) return
        if (primaryIsMouse) return
        if (!grabbed && !touchpadMode()) moveCursor(x, y)
        if (leftHeld) press(BUTTON_LEFT, pressed = false)
    }

    private fun twoFingerMove() {
        val centroidY = (primaryY + secondaryY) / 2f
        val dy = centroidY - twoFingerLastCentroidY
        twoFingerLastCentroidY = centroidY
        if (distance(primaryX, primaryY, primaryDownX, primaryDownY) > MOVE_SLOP_PX ||
            distance(secondaryX, secondaryY, secondaryDownX, secondaryDownY) > MOVE_SLOP_PX
        ) {
            twoFingerMoved = true
        }
        if (twoFingerMoved) {
            scrollRemainder += dy
            val ticks = (scrollRemainder / SCROLL_PX_PER_TICK).toInt()
            if (ticks != 0) {
                scrollRemainder -= ticks * SCROLL_PX_PER_TICK
                // Content follows the fingers: dragging down scrolls up, as in a touchscreen list.
                // Sign to be confirmed on a device.
                GLFW.sendScrollEvent(0.0, ticks.toDouble())
            }
        }
    }

    private fun endTwoFingerGesture(eventTime: Long) {
        val quick = eventTime - twoFingerStartAt <= TWO_FINGER_TAP_MS
        if (!twoFingerMoved && quick && !gesturesDisabled()) {
            press(BUTTON_RIGHT)
            press(BUTTON_RIGHT, pressed = false)
        }
        if (leftHeld) press(BUTTON_LEFT, pressed = false)
        twoFingerMoved = false
        scrollRemainder = 0f
    }

    private fun onLongPress() {
        if (primaryId == NO_POINTER || primaryMoved || secondaryId != NO_POINTER || ignoreUntilUp) return
        if (primaryIsMouse || gesturesDisabled()) return
        // Left stays held, so a long press while mining does not stop the mining.
        press(BUTTON_RIGHT)
        press(BUTTON_RIGHT, pressed = false)
    }

    // ---- Sending -----------------------------------------------------------------------------------

    /** Presses or releases a button for the fingers. The left and right buttons track their own state, so a release is never sent twice. */
    private fun press(button: Int, pressed: Boolean = true) {
        when (button) {
            BUTTON_LEFT -> {
                if (pressed == leftHeld) return
                leftHeld = pressed
            }
            BUTTON_RIGHT -> {
                if (pressed == rightHeld) return
                rightHeld = pressed
            }
        }
        HeldInputs.setButton(FINGER, button, pressed)
    }

    private fun moveCursor(x: Float, y: Float) {
        cursorX = x.toDouble()
        cursorY = y.toDouble()
        clampCursor()
        sendPosition()
    }

    private fun addRelative(dx: Double, dy: Double) {
        val sensitivity = GameInputSettings.sensitivity.toDouble()
        cursorX += dx * sensitivity
        cursorY += dy * sensitivity
        clampCursor()
        sendPosition()
    }

    private fun sendPosition() {
        if (!GameSurfaceBridge.inputReady) return
        GLFW.sendMousePosition0(cursorX, cursorY)
    }

    private fun clampCursor() {
        if (windowWidth <= 0 || windowHeight <= 0) return
        cursorX = cursorX.coerceIn(0.0, (windowWidth - 1).toDouble())
        cursorY = cursorY.coerceIn(0.0, (windowHeight - 1).toDouble())
    }

    private fun touchpadMode(): Boolean = GameInputSettings.touchpadMode
    private fun gesturesDisabled(): Boolean = GameInputSettings.gesturesDisabled

    private fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun glfwButton(androidButton: Int): Int = when (androidButton) {
        MotionEvent.BUTTON_PRIMARY -> BUTTON_LEFT
        MotionEvent.BUTTON_SECONDARY -> BUTTON_RIGHT
        MotionEvent.BUTTON_TERTIARY -> BUTTON_MIDDLE
        MotionEvent.BUTTON_BACK -> BUTTON_4
        MotionEvent.BUTTON_FORWARD -> BUTTON_5
        else -> -1
    }

    private fun modsFrom(metaState: Int): Int {
        var mods = 0
        if ((metaState and KeyEvent.META_SHIFT_ON) != 0) mods = mods or MOD_SHIFT
        if ((metaState and KeyEvent.META_CTRL_ON) != 0) mods = mods or MOD_CONTROL
        if ((metaState and KeyEvent.META_ALT_ON) != 0) mods = mods or MOD_ALT
        if ((metaState and KeyEvent.META_META_ON) != 0) mods = mods or MOD_SUPER
        if ((metaState and KeyEvent.META_CAPS_LOCK_ON) != 0) mods = mods or MOD_CAPS_LOCK
        if ((metaState and KeyEvent.META_NUM_LOCK_ON) != 0) mods = mods or MOD_NUM_LOCK
        return mods
    }
}

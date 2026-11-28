package com.voidlauncher.app.data.launch

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

/**
 * Splits the game surface's touches between the on-screen buttons and [GameInput], and runs what the buttons send.
 *
 * A finger is routed once, when it goes down. A finger that lands on a visible button holds that button until it
 * lifts, even if it slides off, unless the button is a swipe button, in which case the finger may move on to the next
 * swipe button it reaches (the same behaviour as Mojo's swipeable buttons). Every other finger is camera and cursor
 * input for [GameInput]. Fingers are tracked by pointer id, so several buttons and the camera can all be used at once.
 *
 * Button behaviour:
 * - A normal button holds its bindings while its finger is down.
 * - A toggle button switches its bindings on at the first lift and off at the next, as Mojo's toggle buttons do.
 * - A joystick moves the four movement keys by the knob's direction. Diagonals hold two keys. In absolute mode the
 *   base moves to where the finger lands. With forward lock on, pulling the knob past its edge holds sprint until
 *   the knob comes back.
 * - A scroll binding sends one tick on press.
 *
 * Main thread only, except [cancelAllFromAnyThread].
 */
object TouchRouter {
    private val mainHandler = Handler(Looper.getMainLooper())

    private sealed class Target {
        object Game : Target()
        object Ignored : Target()

        /** A finger holding the button with this id. */
        class OnButton(val buttonId: String) : Target()

        /** A finger that left a swipe button and holds nothing. It presses the next swipe button it reaches. */
        object Swiping : Target()
    }

    private val targets = HashMap<Int, Target>()

    /** The pointer id that holds each button. */
    private val heldBy = HashMap<String, Int>()

    /** Toggle buttons that are switched on. */
    private val toggledOn = HashSet<String>()

    /** Joysticks whose forward lock is on. */
    private val lockedForward = HashSet<String>()

    /** Where each absolute joystick's base is, while it is held. */
    private val joystickCentres = HashMap<String, ScreenPosition>()

    private var surfaceWidth = 0
    private var surfaceHeight = 0

    /** Routes one touch event from the game surface. Always consumes it, as the game screen takes every touch. */
    fun onMotion(surface: View, event: MotionEvent): Boolean {
        surfaceWidth = surface.width
        surfaceHeight = surface.height
        if (!GameSurfaceBridge.inputReady) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> pointerDown(event, event.actionIndex)
            MotionEvent.ACTION_MOVE -> for (i in 0 until event.pointerCount) pointerMove(event, i)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> pointerUp(event, event.actionIndex)
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    /**
     * Called when Minecraft moves between the world and a menu. Buttons that are no longer shown stop holding
     * anything, and toggles switch off. A finger still down keeps its button until it lifts, but presses nothing new.
     */
    fun onContextChanged() {
        GameInput.releaseHeld()
        HeldInputs.releaseAll()
        toggledOn.clear()
        lockedForward.clear()
        TouchControls.clearPressed()
        TouchControls.clearJoysticks()
    }

    /** Ends every touch: releases every held key and button and clears the routing state. Main thread only. */
    fun cancelAll() {
        clearRouting()
        GameInput.releaseHeld()
        HeldInputs.releaseAll()
        TouchControls.clearPressed()
        TouchControls.clearJoysticks()
        GameInput.onAllPointersUp()
    }

    /** [cancelAll] from any thread, run on the main thread. */
    fun cancelAllFromAnyThread() {
        mainHandler.post { cancelAll() }
    }

    /** Clears the routing state at the start of a launch. Sends nothing. Main thread only. */
    fun reset() {
        clearRouting()
        TouchControls.clearPressed()
        TouchControls.clearJoysticks()
    }

    private fun clearRouting() {
        targets.clear()
        heldBy.clear()
        toggledOn.clear()
        lockedForward.clear()
        joystickCentres.clear()
    }

    private fun pointerDown(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)
        val isMouse = event.getToolType(index) == MotionEvent.TOOL_TYPE_MOUSE
        val button = hitButton(x, y)
        if (button == null) {
            targets[id] = Target.Game
            GameInput.onPointer(PointerAction.DOWN, id, x, y, isMouse, event.eventTime)
            return
        }
        // A button already held by another finger takes this one but does nothing, so it cannot start a camera gesture.
        if (heldBy.containsKey(button.id)) {
            targets[id] = Target.Ignored
            return
        }
        press(button, id, x, y)
    }

    private fun pointerMove(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)
        when (val target = targets[id] ?: return) {
            Target.Game -> GameInput.onPointer(PointerAction.MOVE, id, x, y, false, event.eventTime)
            Target.Ignored -> Unit
            is Target.OnButton -> moveOnButton(id, target.buttonId, x, y)
            Target.Swiping -> enterSwipeButton(id, x, y)
        }
    }

    private fun pointerUp(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)
        when (val target = targets.remove(id)) {
            Target.Game -> GameInput.onPointer(PointerAction.UP, id, x, y, false, event.eventTime)
            is Target.OnButton -> release(target.buttonId, id)
            Target.Ignored, Target.Swiping, null -> Unit
        }
        if (targets.isEmpty()) GameInput.onAllPointersUp()
    }

    /** Presses a button for [pointer]. Joysticks and normal buttons act now, and a toggle acts when the finger lifts. */
    private fun press(button: TouchButton, pointer: Int, x: Float, y: Float) {
        targets[pointer] = Target.OnButton(button.id)
        heldBy[button.id] = pointer
        TouchControls.setPressed(button.id, true)
        when {
            button.isJoystick -> {
                if (button.absolute) joystickCentres[button.id] = clampToScreen(x, y)
                moveJoystick(button, x, y)
            }
            button.toggle -> Unit
            else -> bind(button.id, button.bindings, down = true)
        }
    }

    /** Lets go of a button held by [pointer]. Does nothing if another finger holds it now. */
    private fun release(buttonId: String, pointer: Int) {
        if (heldBy[buttonId] != pointer) return
        heldBy.remove(buttonId)
        val button = TouchControls.layout.value.button(buttonId)
        when {
            button == null || button.isJoystick -> endJoystick(buttonId)
            button.toggle -> {
                if (toggledOn.remove(buttonId)) {
                    HeldInputs.releaseOwner(buttonId)
                } else {
                    toggledOn.add(buttonId)
                    bind(buttonId, button.bindings, down = true)
                }
            }
            else -> HeldInputs.releaseOwner(buttonId)
        }
        TouchControls.setPressed(buttonId, toggledOn.contains(buttonId))
    }

    /** A finger that started on a swipe button moved. It either stays on that button or leaves it. */
    private fun moveOnButton(pointer: Int, buttonId: String, x: Float, y: Float) {
        if (heldBy[buttonId] != pointer) return
        val button = TouchControls.layout.value.button(buttonId)
        if (button == null) {
            release(buttonId, pointer)
            targets[pointer] = Target.Swiping
            return
        }
        if (button.isJoystick) {
            moveJoystick(button, x, y)
            return
        }
        if (!button.swipe || hitBtn(button, x, y)) return
        // The finger left a swipe button: let go, then press the swipe button under it if there is one.
        release(buttonId, pointer)
        targets[pointer] = Target.Swiping
        enterSwipeButton(pointer, x, y)
    }

    private fun enterSwipeButton(pointer: Int, x: Float, y: Float) {
        val button = TouchControls.visibleButtons(GameInput.inWorld.value).asReversed()
            .firstOrNull { it.swipe && !heldBy.containsKey(it.id) && hitBtn(it, x, y) }
            ?: return
        press(button, pointer, x, y)
    }

    private fun endJoystick(buttonId: String) {
        HeldInputs.releaseOwner(buttonId)
        joystickCentres.remove(buttonId)
        lockedForward.remove(buttonId)
        TouchControls.setJoystick(buttonId, null)
        TouchControls.setPressed(buttonId, false)
    }

    /** Presses or releases the bindings of [buttonId]. Scroll bindings send a tick on press only. */
    private fun bind(owner: String, bindings: List<Binding>, down: Boolean) {
        for (binding in bindings) {
            when (binding) {
                is Binding.Key -> HeldInputs.setKey(owner, binding.glfwKey, down)
                is Binding.Mouse -> HeldInputs.setButton(owner, binding.button, down)
                Binding.ScrollUp -> if (down) HeldInputs.scrollTick(-1)
                Binding.ScrollDown -> if (down) HeldInputs.scrollTick(1)
            }
        }
    }

    /**
     * Moves the knob toward the finger and holds the movement keys that the deflection selects. The knob stays inside
     * the base, and a finger further away than the base edge holds the same keys as the edge.
     */
    private fun moveJoystick(button: TouchButton, x: Float, y: Float) {
        val id = button.id
        val settings = TouchControls.settings.value
        val (centreX, centreY) = joystickCentres[id] ?: TouchGeometry.centrePx(button, surfaceWidth, surfaceHeight)
        val radius = TouchGeometry.radiusPx(button, settings.scale, surfaceWidth, surfaceHeight)
        if (radius <= 0f) return
        val rawX = (x - centreX) / radius
        val rawY = (y - centreY) / radius
        val (dx, dy) = TouchGeometry.clampToBase(rawX, rawY)
        TouchControls.setJoystick(
            id,
            TouchControls.JoystickState(centreX, centreY, centreX + dx * radius, centreY + dy * radius),
        )
        val wanted = TouchGeometry.joystickKeys(dx, dy)
        for (key in TouchGeometry.JOYSTICK_KEYS) HeldInputs.setKey(id, key, key in wanted)
        if (button.forwardLock) updateForwardLock(id, rawY)
    }

    /** Holds sprint while the knob is pulled past the base edge, and lets it go when the knob comes back. */
    private fun updateForwardLock(id: String, rawY: Float) {
        val sprint = GameAction.SPRINT.glfwKey
        if (id !in lockedForward && TouchGeometry.forwardLockPulled(rawY)) {
            lockedForward.add(id)
            HeldInputs.setKey(id, sprint, true)
        } else if (id in lockedForward && TouchGeometry.forwardLockReleased(rawY)) {
            lockedForward.remove(id)
            HeldInputs.setKey(id, sprint, false)
        }
    }

    /** The visible button under a touch at ([x], [y]) pixels, or null if the touch is for the camera. */
    private fun hitButton(x: Float, y: Float): TouchButton? {
        if (!TouchControls.settings.value.enabled) return null
        return TouchControls.visibleButtons(GameInput.inWorld.value).asReversed().firstOrNull { hitBtn(it, x, y) }
    }

    private fun hitBtn(button: TouchButton, x: Float, y: Float): Boolean = TouchGeometry.hit(
        button,
        TouchControls.settings.value.scale,
        surfaceWidth,
        surfaceHeight,
        x,
        y,
        centre = joystickCentres[button.id] ?: TouchGeometry.centrePx(button, surfaceWidth, surfaceHeight),
    )

    private fun clampToScreen(x: Float, y: Float): ScreenPosition =
        x.coerceIn(0f, surfaceWidth.toFloat()) to y.coerceIn(0f, surfaceHeight.toFloat())
}

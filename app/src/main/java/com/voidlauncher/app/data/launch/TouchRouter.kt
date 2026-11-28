package com.voidlauncher.app.data.launch

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.sqrt

/**
 * Splits the game surface's touches between the on-screen controls and [GameInput].
 *
 * A finger is routed once, when it goes down. A finger that lands on a visible control belongs to that control until
 * it lifts, even if it slides off. Every other finger is camera and cursor input for [GameInput]. Fingers are tracked
 * by pointer id, so controls and the camera can all be used at once. A second finger on a control that is already
 * held is ignored, so it cannot start a camera gesture.
 *
 * Main thread only, except [cancelAllFromAnyThread].
 */
object TouchRouter {
    private val mainHandler = Handler(Looper.getMainLooper())

    private sealed class Target {
        object Game : Target()
        object Ignored : Target()
        class Control(val control: TouchControl) : Target()
    }

    private val targets = HashMap<Int, Target>()

    /** The pointer id that holds each control. */
    private val heldBy = HashMap<TouchControl, Int>()

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
     * Called when Minecraft moves between the world and a menu. Controls that are no longer shown stop holding
     * anything. A finger still down keeps its control until it lifts, but it presses nothing new.
     */
    fun onContextChanged() {
        GameInput.releaseHeld()
        HeldInputs.releaseAll()
        TouchControls.clearPressed()
        TouchControls.setKnob(null)
    }

    /** Ends every touch: releases every held key and button and clears the routing state. Main thread only. */
    fun cancelAll() {
        targets.clear()
        heldBy.clear()
        GameInput.releaseHeld()
        HeldInputs.releaseAll()
        TouchControls.clearPressed()
        TouchControls.setKnob(null)
        GameInput.onAllPointersUp()
    }

    /** [cancelAll] from any thread, run on the main thread. */
    fun cancelAllFromAnyThread() {
        mainHandler.post { cancelAll() }
    }

    /** Clears the routing state at the start of a launch. Sends nothing. Main thread only. */
    fun reset() {
        targets.clear()
        heldBy.clear()
        TouchControls.clearPressed()
        TouchControls.setKnob(null)
    }

    private fun pointerDown(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)
        val isMouse = event.getToolType(index) == MotionEvent.TOOL_TYPE_MOUSE
        val control = hitControl(x, y)
        if (control == null) {
            targets[id] = Target.Game
            GameInput.onPointer(PointerAction.DOWN, id, x, y, isMouse, event.eventTime)
            return
        }
        if (heldBy.containsKey(control)) {
            targets[id] = Target.Ignored
            return
        }
        targets[id] = Target.Control(control)
        heldBy[control] = id
        TouchControls.setPressed(control, true)
        when (val action = control.action) {
            ControlAction.Joystick -> moveJoystick(control, x, y)
            is ControlAction.HoldKey -> HeldInputs.setKey(control, action.key, true)
            is ControlAction.HoldMouse -> HeldInputs.setButton(control, action.button, true)
            is ControlAction.TapKey -> HeldInputs.tapKey(control, action.key)
        }
    }

    private fun pointerMove(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)
        when (val target = targets[id] ?: return) {
            Target.Game -> GameInput.onPointer(PointerAction.MOVE, id, x, y, false, event.eventTime)
            Target.Ignored -> Unit
            is Target.Control -> {
                val control = target.control
                val joystick = control.action == ControlAction.Joystick
                if (joystick && heldBy[control] == id && control.isVisible(GameInput.inWorld.value)) {
                    moveJoystick(control, x, y)
                }
            }
        }
    }

    private fun pointerUp(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)
        when (val target = targets.remove(id)) {
            Target.Game -> GameInput.onPointer(PointerAction.UP, id, x, y, false, event.eventTime)
            is Target.Control -> releaseControl(target.control, id)
            Target.Ignored, null -> Unit
        }
        if (targets.isEmpty()) GameInput.onAllPointersUp()
    }

    private fun releaseControl(control: TouchControl, id: Int) {
        if (heldBy[control] != id) return
        heldBy.remove(control)
        HeldInputs.releaseOwner(control)
        TouchControls.setPressed(control, false)
        if (control.action == ControlAction.Joystick) TouchControls.setKnob(null)
    }

    /** The visible control under a touch at ([x], [y]) pixels, or null if the touch is for the camera. */
    private fun hitControl(x: Float, y: Float): TouchControl? {
        val settings = TouchControls.settings.value
        if (!settings.enabled) return null
        val inWorld = GameInput.inWorld.value
        val layout = TouchControls.layout.value
        return TouchControls.visibleControls(inWorld).firstOrNull { control ->
            val (fx, fy) = layout[control] ?: return@firstOrNull false
            TouchGeometry.hit(control, fx, fy, settings.scale, surfaceWidth, surfaceHeight, x, y)
        }
    }

    /**
     * Moves the joystick knob toward the finger and holds the movement keys that the deflection selects. The knob
     * stays inside the base, and a finger further away than the base edge holds the same keys as the edge.
     */
    private fun moveJoystick(control: TouchControl, x: Float, y: Float) {
        val (fx, fy) = TouchControls.layout.value[control] ?: return
        val scale = TouchControls.settings.value.scale
        val radius = TouchGeometry.radiusPx(control, scale, surfaceWidth, surfaceHeight)
        if (radius <= 0f) return
        val centerX = fx * surfaceWidth
        val centerY = fy * surfaceHeight
        var dx = (x - centerX) / radius
        var dy = (y - centerY) / radius
        val length = sqrt(dx * dx + dy * dy)
        if (length > 1f) {
            dx /= length
            dy /= length
        }
        TouchControls.setKnob((centerX + dx * radius) to (centerY + dy * radius))
        val wanted = TouchGeometry.joystickKeys(dx, dy)
        for (key in TouchGeometry.JOYSTICK_KEYS) HeldInputs.setKey(control, key, key in wanted)
    }
}

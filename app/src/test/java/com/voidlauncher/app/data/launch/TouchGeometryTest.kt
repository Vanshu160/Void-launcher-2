package com.voidlauncher.app.data.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchGeometryTest {
    private val forward = GameAction.FORWARD.glfwKey
    private val backward = GameAction.BACKWARD.glfwKey
    private val left = GameAction.LEFT.glfwKey
    private val right = GameAction.RIGHT.glfwKey

    @Test
    fun joystickCentreHoldsNoKeys() {
        assertTrue(TouchGeometry.joystickKeys(0f, 0f).isEmpty())
        // Inside the dead zone on both axes.
        assertTrue(TouchGeometry.joystickKeys(0.2f, -0.2f).isEmpty())
    }

    @Test
    fun joystickHoldsOneKeyPerAxisDirection() {
        assertEquals(setOf(forward), TouchGeometry.joystickKeys(0f, -1f))
        assertEquals(setOf(backward), TouchGeometry.joystickKeys(0f, 1f))
        assertEquals(setOf(left), TouchGeometry.joystickKeys(-1f, 0f))
        assertEquals(setOf(right), TouchGeometry.joystickKeys(1f, 0f))
    }

    @Test
    fun joystickDiagonalsHoldTwoKeys() {
        // Screen y grows downwards, so up and to the right is forward and right.
        assertEquals(setOf(forward, right), TouchGeometry.joystickKeys(0.8f, -0.8f))
        assertEquals(setOf(backward, left), TouchGeometry.joystickKeys(-0.8f, 0.8f))
    }

    @Test
    fun joystickKeysAreTheFourMovementKeys() {
        assertEquals(setOf(forward, backward, left, right), TouchGeometry.JOYSTICK_KEYS.toSet())
    }

    @Test
    fun profileNamesOrientationAndAspectRatio() {
        assertEquals("land-2.2", TouchGeometry.profileFor(2392, 1080))
        assertEquals("port-2.2", TouchGeometry.profileFor(1080, 2392))
        assertEquals("land-1.8", TouchGeometry.profileFor(1920, 1080))
        assertEquals("unknown", TouchGeometry.profileFor(0, 0))
    }

    @Test
    fun parsePositionReadsAndClampsSavedValues() {
        assertEquals(0.5f to 0.25f, TouchGeometry.parsePosition("0.5,0.25"))
        assertEquals(TouchGeometry.MAX_POSITION to TouchGeometry.MIN_POSITION, TouchGeometry.parsePosition("2,-1"))
    }

    @Test
    fun parsePositionRejectsMalformedValues() {
        assertNull(TouchGeometry.parsePosition(null))
        assertNull(TouchGeometry.parsePosition(""))
        assertNull(TouchGeometry.parsePosition("0.5"))
        assertNull(TouchGeometry.parsePosition("a,b"))
        assertNull(TouchGeometry.parsePosition("NaN,0.5"))
        assertNull(TouchGeometry.parsePosition("0.1,0.2,0.3"))
    }

    @Test
    fun formatThenParseKeepsThePosition() {
        val position = 0.125f to 0.875f
        assertEquals(position, TouchGeometry.parsePosition(TouchGeometry.formatPosition(position)))
    }

    @Test
    fun hitTestUsesTheControlRadius() {
        // A 1000x1000 screen with the jump button centred at (0.5, 0.5). Its radius is 0.14 * 1000 / 2 = 70 px,
        // with 15% slop on top of that.
        val jump = TouchControl.JUMP
        assertTrue(TouchGeometry.hit(jump, 0.5f, 0.5f, 1f, 1000, 1000, 500f, 500f))
        assertTrue(TouchGeometry.hit(jump, 0.5f, 0.5f, 1f, 1000, 1000, 500f + 75f, 500f))
        assertFalse(TouchGeometry.hit(jump, 0.5f, 0.5f, 1f, 1000, 1000, 500f + 120f, 500f))
    }

    @Test
    fun scaleChangesTheHitRadius() {
        val jump = TouchControl.JUMP
        assertFalse(TouchGeometry.hit(jump, 0.5f, 0.5f, 0.5f, 1000, 1000, 500f + 75f, 500f))
        assertTrue(TouchGeometry.hit(jump, 0.5f, 0.5f, 1.5f, 1000, 1000, 500f + 75f, 500f))
    }

    @Test
    fun escapeIsShownInMenusAndWorldButOthersOnlyInTheWorld() {
        assertTrue(TouchControl.ESCAPE.isVisible(inWorld = false))
        assertTrue(TouchControl.ESCAPE.isVisible(inWorld = true))
        assertFalse(TouchControl.JOYSTICK.isVisible(inWorld = false))
        assertTrue(TouchControl.JOYSTICK.isVisible(inWorld = true))
    }

    @Test
    fun everyControlHasADefaultInsideTheScreen() {
        for (control in TouchControl.entries) {
            assertTrue(control.defaultX in TouchGeometry.MIN_POSITION..TouchGeometry.MAX_POSITION)
            assertTrue(control.defaultY in TouchGeometry.MIN_POSITION..TouchGeometry.MAX_POSITION)
        }
    }
}

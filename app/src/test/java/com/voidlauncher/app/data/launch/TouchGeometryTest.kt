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

    private fun button(x: Float = 0.5f, y: Float = 0.5f, anchorRight: Boolean = false, size: Float = 1f) = TouchButton(
        id = "jump",
        name = "Jump",
        x = x,
        y = y,
        anchorRight = anchorRight,
        size = size,
    )

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
    fun clampToBaseKeepsTheDirectionAndLimitsTheLength() {
        val (x, y) = TouchGeometry.clampToBase(3f, 4f)
        assertEquals(0.6f, x, 1e-5f)
        assertEquals(0.8f, y, 1e-5f)
        assertEquals(0.3f to 0.4f, TouchGeometry.clampToBase(0.3f, 0.4f))
    }

    @Test
    fun forwardLockNeedsAPullPastTheBaseEdgeAndReleasesOnReturn() {
        assertTrue(TouchGeometry.forwardLockPulled(-1.6f))
        assertFalse(TouchGeometry.forwardLockPulled(-1.0f))
        assertTrue(TouchGeometry.forwardLockReleased(-0.5f))
        assertFalse(TouchGeometry.forwardLockReleased(-1.2f))
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
    fun anchorFollowsTheNearerEdge() {
        val anchor = TouchGeometry.anchorFor(0.2f, 0.8f)
        assertFalse(anchor.right)
        assertTrue(anchor.bottom)
        assertEquals(0.2f, anchor.x, 1e-5f)
        assertEquals(0.2f, anchor.y, 1e-5f)
        assertTrue(TouchGeometry.anchorFor(0.9f, 0.5f).right)
    }

    @Test
    fun anchorStaysOnScreen() {
        // A centre at the very edge is pulled in, so the button cannot be placed off screen.
        val anchor = TouchGeometry.anchorFor(0.99f, 0.5f)
        assertTrue(anchor.right)
        assertEquals(TouchGeometry.MIN_POSITION, anchor.x, 1e-5f)
    }

    @Test
    fun rightAnchoredButtonsMeasureFromTheRightEdge() {
        val centre = TouchGeometry.centrePx(button(x = 0.1f, anchorRight = true), 1000, 1000)
        assertEquals(900f, centre.first, 1e-3f)
        assertEquals(500f, centre.second, 1e-3f)
    }

    @Test
    fun radiusFollowsTheShorterSideScaleAndButtonSize() {
        assertEquals(70f, TouchGeometry.radiusPx(button(), 1f, 1000, 1000), 1e-3f)
        assertEquals(140f, TouchGeometry.radiusPx(button(size = 2f), 1f, 1000, 1000), 1e-3f)
        assertEquals(180f, TouchGeometry.radiusPx(joystick(), 1f, 1000, 1000), 1e-3f)
    }

    @Test
    fun hitTestUsesTheButtonRadius() {
        // A 1000x1000 screen with the button centred at (0.5, 0.5). Its radius is 70 px, with 15% slop on top.
        val jump = button()
        assertTrue(TouchGeometry.hit(jump, 1f, 1000, 1000, 500f, 500f))
        assertTrue(TouchGeometry.hit(jump, 1f, 1000, 1000, 500f + 75f, 500f))
        assertFalse(TouchGeometry.hit(jump, 1f, 1000, 1000, 500f + 120f, 500f))
    }

    @Test
    fun scaleChangesTheHitRadius() {
        val jump = button()
        assertFalse(TouchGeometry.hit(jump, 0.5f, 1000, 1000, 500f + 75f, 500f))
        assertTrue(TouchGeometry.hit(jump, 1.5f, 1000, 1000, 500f + 75f, 500f))
    }

    @Test
    fun hitTestCanUseAMovedCentre() {
        // An absolute joystick's base is where the finger landed, not where the layout puts it.
        val stick = joystick()
        assertTrue(TouchGeometry.hit(stick, 1f, 1000, 1000, 800f, 800f, centre = 800f to 800f))
        assertFalse(TouchGeometry.hit(stick, 1f, 1000, 1000, 800f, 800f))
    }

    @Test
    fun everyDefaultIsInsideTheScreen() {
        for (button in TouchLayouts.mojoDefault().buttons) {
            val (fx, fy) = TouchGeometry.centreFraction(button)
            assertTrue(fx in TouchGeometry.MIN_POSITION..TouchGeometry.MAX_POSITION)
            assertTrue(fy in TouchGeometry.MIN_POSITION..TouchGeometry.MAX_POSITION)
        }
    }

    private fun joystick() = TouchButton(id = "move", name = "Move", kind = ButtonKind.JOYSTICK)
}

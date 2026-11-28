package com.voidlauncher.app.data.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeldCodesTest {
    private val leftButton = 0
    private val attack = Any()
    private val camera = Any()

    @Test
    fun firstOwnerPressesAndLastOwnerReleases() {
        val held = HeldCodes()
        assertEquals(true, held.set(attack, leftButton, true))
        // A second owner of the same code does not press it again.
        assertNull(held.set(camera, leftButton, true))
        // The first owner letting go does not release a code the camera still holds.
        assertNull(held.set(attack, leftButton, false))
        assertTrue(held.isHeld(leftButton))
        // The last owner releases it.
        assertEquals(false, held.set(camera, leftButton, false))
        assertFalse(held.isHeld(leftButton))
    }

    @Test
    fun repeatedDownAndUpFromOneOwnerChangesNothing() {
        val held = HeldCodes()
        assertEquals(true, held.set(attack, leftButton, true))
        assertNull(held.set(attack, leftButton, true))
        assertEquals(false, held.set(attack, leftButton, false))
        assertNull(held.set(attack, leftButton, false))
    }

    @Test
    fun releaseOwnerReleasesOnlyItsOwnCodes() {
        val held = HeldCodes()
        held.set(attack, leftButton, true)
        held.set(attack, 1, true)
        held.set(camera, leftButton, true)
        // The attack owner releases its right button, but its left button stays held by the camera owner.
        assertEquals(listOf(1), held.releaseOwner(attack))
        assertTrue(held.isHeld(leftButton))
        assertFalse(held.isHeld(1))
    }

    @Test
    fun releaseAllClearsEverything() {
        val held = HeldCodes()
        held.set(attack, leftButton, true)
        held.set(camera, leftButton, true)
        held.set(camera, 87, true)
        val released = held.releaseAll().sorted()
        assertEquals(listOf(87, leftButton).sorted(), released)
        assertFalse(held.isHeld(leftButton))
        assertFalse(held.isHeld(87))
        assertTrue(held.releaseAll().isEmpty())
    }

    @Test
    fun releaseOfAnUnknownOwnerDoesNothing() {
        val held = HeldCodes()
        assertTrue(held.releaseOwner(attack).isEmpty())
        assertNull(held.set(attack, 32, false))
    }
}

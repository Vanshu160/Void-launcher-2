package com.voidlauncher.app.data.launch

import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutNamesTest {

    @Test
    fun blankNamesBecomeAPlainName() {
        assertEquals("Layout", TouchLayouts.cleanName("   \n\t "))
    }

    @Test
    fun linesAreJoinedWithSpaces() {
        assertEquals("first second", TouchLayouts.cleanName("first\nsecond"))
    }

    @Test
    fun longNamesAreCutToTheLimit() {
        assertEquals(TouchLayouts.MAX_NAME_LENGTH, TouchLayouts.cleanName("x".repeat(40)).length)
    }

    @Test
    fun surroundingSpacesAreRemoved() {
        assertEquals("Night PvP", TouchLayouts.cleanName("  Night PvP  "))
    }
}

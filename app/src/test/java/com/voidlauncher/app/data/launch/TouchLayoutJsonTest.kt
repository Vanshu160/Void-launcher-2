package com.voidlauncher.app.data.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TouchLayoutJsonTest {

    @Test
    fun theDefaultLayoutSurvivesARoundTrip() {
        val layout = TouchLayouts.mojoDefault()
        assertEquals(layout, TouchLayoutJson.decode(TouchLayoutJson.encode(layout)))
    }

    @Test
    fun everyBindingKindSurvivesARoundTrip() {
        val layout = TouchLayout(
            listOf(
                TouchButton(
                    id = "mixed",
                    name = "Mixed",
                    bindings = listOf(
                        Binding.Key(87),
                        Binding.Mouse(1),
                        Binding.ScrollUp,
                        Binding.ScrollDown,
                    ),
                    x = 0.25f,
                    y = 0.75f,
                    anchorRight = true,
                    anchorBottom = true,
                    size = 1.5f,
                    opacity = 0.5f,
                    background = 0x66B388FFL,
                    stroke = 0xFF42A5F5L,
                    cornerRadius = 0.2f,
                    toggle = true,
                    swipe = true,
                    inWorld = false,
                    inMenu = true,
                ),
                TouchButton(
                    id = "stick",
                    name = "Stick",
                    kind = ButtonKind.JOYSTICK,
                    forwardLock = true,
                    absolute = true,
                ),
            ),
        )
        assertEquals(layout, TouchLayoutJson.decode(TouchLayoutJson.encode(layout)))
    }

    @Test
    fun namesWithQuotesBackslashesAndControlCharactersSurvive() {
        val name = "He said \"hi\" \\ then\nleft\ttab"
        val layout = TouchLayout(listOf(TouchButton(id = "odd", name = name)))
        assertEquals(name, TouchLayoutJson.decode(TouchLayoutJson.encode(layout)).buttons.single().name)
    }

    @Test
    fun wrongFormatIsRefused() {
        expectFormatError("{\"format\":\"something-else\",\"version\":1,\"buttons\":[]}")
    }

    @Test
    fun futureVersionIsRefused() {
        expectFormatError("{\"format\":\"void-touch-layout\",\"version\":2,\"buttons\":[]}")
    }

    @Test
    fun malformedTextIsRefused() {
        expectFormatError("{")
        expectFormatError("[1,2")
        expectFormatError("{\"format\":\"void-touch-layout\",\"version\":1,\"buttons\":[] trailing")
        expectFormatError("")
    }

    @Test
    fun tooManyButtonsAreRefused() {
        val entries = (1..(TouchLayouts.MAX_BUTTONS + 1)).joinToString(",") { "{\"id\":\"b$it\"}" }
        expectFormatError("{\"format\":\"void-touch-layout\",\"version\":1,\"buttons\":[$entries]}")
    }

    @Test
    fun entriesWithoutIdsAndRepeatedIdsAreDropped() {
        val text = """{"format":"void-touch-layout","version":1,"buttons":[
            {"name":"no id"},
            {"id":"","name":"empty id"},
            {"id":"same","name":"first"},
            {"id":"same","name":"second"},
            "not an object"
        ]}"""
        val layout = TouchLayoutJson.decode(text)
        assertEquals(listOf("first"), layout.buttons.map { it.name })
    }

    @Test
    fun numbersAreClampedToTheEditorRanges() {
        val text = """{"format":"void-touch-layout","version":1,"buttons":[
            {"id":"a","x":5,"y":-3,"size":9,"opacity":-1,"cornerRadius":4}
        ]}"""
        val button = TouchLayoutJson.decode(text).buttons.single()
        assertEquals(TouchGeometry.MAX_POSITION, button.x, 1e-5f)
        assertEquals(TouchGeometry.MIN_POSITION, button.y, 1e-5f)
        assertEquals(2f, button.size, 1e-5f)
        assertEquals(0.2f, button.opacity, 1e-5f)
        assertEquals(1f, button.cornerRadius, 1e-5f)
    }

    @Test
    fun bindingsAreCappedAndUnknownOnesSkipped() {
        val keys = (1..6).joinToString(",") { "{\"type\":\"key\",\"code\":$it}" }
        val text = """{"format":"void-touch-layout","version":1,"buttons":[
            {"id":"a","bindings":[{"type":"weird"},$keys]}
        ]}"""
        val bindings = TouchLayoutJson.decode(text).buttons.single().bindings
        assertEquals(TouchLayouts.MAX_BINDINGS, bindings.size)
        assertEquals(Binding.Key(1), bindings.first())
    }

    @Test
    fun joystickIgnoresBindingsAndButtonOnlyFlags() {
        val text = """{"format":"void-touch-layout","version":1,"buttons":[
            {"id":"stick","kind":"joystick","bindings":[{"type":"key","code":87}],"toggle":true}
        ]}"""
        val button = TouchLayoutJson.decode(text).buttons.single()
        assertTrue(button.isJoystick)
        assertTrue(button.bindings.isEmpty())
        assertFalse(button.forwardLock)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val text = """{"format":"void-touch-layout","version":1,"future":true,"buttons":[{"id":"a","hue":"blue"}]}"""
        assertEquals("a", TouchLayoutJson.decode(text).buttons.single().id)
    }

    private fun expectFormatError(text: String) {
        try {
            TouchLayoutJson.decode(text)
            fail("Expected a format error for: $text")
        } catch (expected: TouchLayoutFormatException) {
            // Expected.
        }
    }
}

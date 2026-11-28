package com.voidlauncher.app.data.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchLayoutTest {

    @Test
    fun defaultLayoutHasUniqueIdsAndAJoystickOnTheLeft() {
        val buttons = TouchLayouts.mojoDefault().buttons
        assertEquals(buttons.size, buttons.map { it.id }.toSet().size)
        val joystick = buttons.single { it.isJoystick }
        assertTrue(joystick.x < 0.5f)
    }

    @Test
    fun defaultLayoutKeepsTheMouseButtonsOfMojo() {
        val buttons = TouchLayouts.mojoDefault().buttons
        assertEquals(listOf(Binding.Mouse(0)), buttons.single { it.id == "attack" }.bindings)
        assertEquals(listOf(Binding.Mouse(1)), buttons.single { it.id == "use" }.bindings)
    }

    @Test
    fun defaultLayoutHasNoMoreBindingsThanTheLimit() {
        for (button in TouchLayouts.mojoDefault().buttons) {
            assertTrue(button.bindings.size <= TouchLayouts.MAX_BINDINGS)
        }
    }

    @Test
    fun joysticksNeverHaveBindings() {
        val joystick = TouchButton(id = "stick", name = "Move", kind = ButtonKind.JOYSTICK)
        assertTrue(joystick.bindings.isEmpty())
        assertTrue(joystick.isJoystick)
    }

    @Test
    fun escapeIsShownInMenusAndWorldButChatOnlyInTheWorld() {
        val buttons = TouchLayouts.mojoDefault().buttons
        val escape = buttons.single { it.id == "escape" }
        val chat = buttons.single { it.id == "chat" }
        assertTrue(escape.inMenu)
        assertTrue(escape.inWorld)
        assertFalse(chat.inMenu)
        assertTrue(chat.inWorld)
    }

    @Test
    fun layoutEditsReplaceAddAndRemoveById() {
        val base = TouchLayout(listOf(TouchButton(id = "a", name = "A")))
        val renamed = base.replace(base.button("a")!!.copy(name = "B"))
        assertEquals("B", renamed.button("a")!!.name)

        val added = renamed.add(TouchButton(id = "b", name = "C"))
        assertEquals(2, added.buttons.size)

        val removed = added.remove("a")
        assertNull(removed.button("a"))
        assertNotEquals(added, removed)
    }

    @Test
    fun newIdsAvoidTheExistingOnes() {
        assertEquals("button1", TouchLayouts.newId(emptySet()))
        assertEquals("button3", TouchLayouts.newId(setOf("button1", "button2")))
    }

    @Test
    fun bindingLabelsNameKeysAndButtons() {
        assertEquals("W", Binding.Key(87).label())
        assertEquals("Ctrl", Binding.Key(341).label())
        assertEquals("F3", Binding.Key(292).label())
        assertEquals("Left click", Binding.Mouse(0).label())
        assertEquals("Right click", Binding.Mouse(1).label())
        assertEquals("Scroll up", Binding.ScrollUp.label())
    }

    @Test
    fun bindingChoicesHaveNoDuplicates() {
        assertEquals(BindingChoices.all.size, BindingChoices.all.toSet().size)
    }
}

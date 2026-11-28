package com.voidlauncher.app.data.launch

/**
 * The on-screen control layout: which buttons exist, what each one sends, and where it sits. The model follows the
 * control types of MojoLauncher's layouts (key buttons, mouse buttons, scroll buttons and a movement joystick, each
 * with up to four bindings) but is written from scratch. See docs/touch-controls.md.
 *
 * No Android dependency, so the model and its defaults can be unit tested.
 */

/** Default fill colour: black at 30 percent, as in Mojo's default buttons. ARGB. */
const val DEFAULT_BACKGROUND_ARGB = 0x4D000000L

/** Default outline colour: opaque white. ARGB. */
const val DEFAULT_STROKE_ARGB = 0xFFFFFFFFL

/** What a button sends to the game while it is held, or once per press for the scroll bindings. */
sealed interface Binding {
    /** A GLFW key code, the codes Minecraft reads. */
    data class Key(val glfwKey: Int) : Binding

    /** A GLFW mouse button: 0 is left, 1 is right and 2 is middle. */
    data class Mouse(val button: Int) : Binding

    /** One scroll tick up, sent on each press. */
    data object ScrollUp : Binding

    /** One scroll tick down, sent on each press. */
    data object ScrollDown : Binding
}

/** The name a binding shows in the editor, such as "W", "Ctrl" or "Left click". */
fun Binding.label(): String = when (this) {
    is Binding.Key -> KeyNames.of(glfwKey)
    is Binding.Mouse -> when (button) {
        0 -> "Left click"
        1 -> "Right click"
        2 -> "Middle click"
        else -> "Mouse button $button"
    }
    Binding.ScrollUp -> "Scroll up"
    Binding.ScrollDown -> "Scroll down"
}

/** Names for the GLFW key codes that the editor offers. */
object KeyNames {
    fun of(glfwKey: Int): String = when (glfwKey) {
        in 65..90 -> glfwKey.toChar().toString()
        in 48..57 -> glfwKey.toChar().toString()
        in 290..301 -> "F${glfwKey - 289}"
        32 -> "Space"
        256 -> "Esc"
        257 -> "Enter"
        258 -> "Tab"
        259 -> "Backspace"
        262 -> "Right"
        263 -> "Left"
        264 -> "Down"
        265 -> "Up"
        340 -> "Shift"
        341 -> "Ctrl"
        342 -> "Alt"
        else -> "Key $glfwKey"
    }
}

/** A button is either a key or mouse button, or the movement joystick. */
enum class ButtonKind { BUTTON, JOYSTICK }

/**
 * One on-screen control.
 *
 * The position is an offset from the nearest edges: [x] is measured from the right edge when [anchorRight] is set, and
 * from the left edge otherwise. [y] works the same way with [anchorBottom]. Both are fractions of the screen size, so
 * a button stays near the same edge when the screen changes shape. [size] multiplies the global control scale, and
 * [opacity] multiplies the global button opacity.
 *
 * [toggle] switches the bindings on with one tap and off with the next. [swipe] lets a finger that slides off the
 * button press the next swipe button it reaches. [forwardLock] and [absolute] apply to joysticks only: the first holds
 * sprint when the stick is pulled past its edge, and the second moves the base to where the finger lands.
 */
data class TouchButton(
    val id: String,
    val name: String,
    val kind: ButtonKind = ButtonKind.BUTTON,
    val bindings: List<Binding> = emptyList(),
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val anchorRight: Boolean = false,
    val anchorBottom: Boolean = false,
    val size: Float = 1f,
    val opacity: Float = 1f,
    val background: Long = DEFAULT_BACKGROUND_ARGB,
    val stroke: Long = DEFAULT_STROKE_ARGB,
    val cornerRadius: Float = 0.5f,
    val toggle: Boolean = false,
    val swipe: Boolean = false,
    val forwardLock: Boolean = false,
    val absolute: Boolean = false,
    val inWorld: Boolean = true,
    val inMenu: Boolean = false,
) {
    val isJoystick: Boolean get() = kind == ButtonKind.JOYSTICK
}

/** A whole layout. Button ids are unique within it. */
data class TouchLayout(val buttons: List<TouchButton>) {
    fun button(id: String): TouchButton? = buttons.firstOrNull { it.id == id }

    fun replace(updated: TouchButton): TouchLayout =
        copy(buttons = buttons.map { if (it.id == updated.id) updated else it })

    fun add(button: TouchButton): TouchLayout = copy(buttons = buttons + button)

    fun remove(id: String): TouchLayout = copy(buttons = buttons.filterNot { it.id == id })
}

object TouchLayouts {
    /** Limits that keep the editor and the saved file small and the touch routing cheap. */
    const val MAX_BUTTONS = 48
    const val MAX_BINDINGS = 4
    const val MAX_NAME_LENGTH = 24

    /** Fill colours the editor offers. */
    val BACKGROUND_PRESETS: List<Long> = listOf(
        DEFAULT_BACKGROUND_ARGB,
        0x66FFFFFFL,
        0x66B388FFL,
        0x6642A5F5L,
        0x80000000L,
    )

    /** Outline colours the editor offers. */
    val STROKE_PRESETS: List<Long> = listOf(
        DEFAULT_STROKE_ARGB,
        0xFFB388FFL,
        0xFF42A5F5L,
        0xFF000000L,
    )

    /**
     * The layout a new install starts with. It follows the arrangement of MojoLauncher's default layout: the mouse
     * buttons on the right, the chat, inventory and drop buttons across the top, and the function keys in the
     * menu row. The four movement keys of Mojo's default are replaced by one joystick on the left.
     */
    fun mojoDefault(): TouchLayout = TouchLayout(
        listOf(
            TouchButton(id = "joystick", name = "Move", kind = ButtonKind.JOYSTICK, x = 0.14f, y = 0.70f),
            key("sprint", "Sprint", 341, 0.36f, 0.90f),
            TouchButton(
                id = "attack",
                name = "Attack",
                bindings = listOf(Binding.Mouse(0)),
                x = 0.78f,
                y = 0.70f,
            ),
            TouchButton(
                id = "use",
                name = "Use",
                bindings = listOf(Binding.Mouse(1)),
                x = 0.92f,
                y = 0.52f,
            ),
            key("jump", "Jump", 32, 0.92f, 0.84f),
            key("sneak", "Sneak", 340, 0.78f, 0.88f, toggle = true),
            key("inventory", "Inventory", 69, 0.66f, 0.14f),
            key("drop", "Drop", 81, 0.79f, 0.14f),
            key("chat", "Chat", 84, 0.92f, 0.14f),
            key("escape", "Esc", 256, 0.07f, 0.14f, inMenu = true),
            key("debug", "F3", 292, 0.20f, 0.14f),
            key("third_person", "F5", 294, 0.33f, 0.14f),
            key("players", "Tab", 258, 0.46f, 0.14f),
        ),
    )

    /** A layout name as the app keeps it: one line, trimmed, at most [MAX_NAME_LENGTH] characters, never blank. */
    fun cleanName(raw: String): String {
        val oneLine = raw.replace(Regex("\\s+"), " ").trim().take(MAX_NAME_LENGTH).trim()
        return oneLine.ifEmpty { "Layout" }
    }

    /** An id that no button in [existing] uses. */
    fun newId(existing: Set<String>): String {
        var number = 1
        while ("button$number" in existing) number++
        return "button$number"
    }

    private fun key(
        id: String,
        name: String,
        glfwKey: Int,
        x: Float,
        y: Float,
        toggle: Boolean = false,
        inMenu: Boolean = false,
    ) = TouchButton(
        id = id,
        name = name,
        bindings = listOf(Binding.Key(glfwKey)),
        x = x,
        y = y,
        toggle = toggle,
        inMenu = inMenu,
    )
}

/** The bindings the editor offers to add. Each one appears once. */
object BindingChoices {
    val all: List<Binding> = buildList {
        add(Binding.Mouse(0))
        add(Binding.Mouse(1))
        add(Binding.Mouse(2))
        add(Binding.ScrollUp)
        add(Binding.ScrollDown)
        for (code in listOf(87, 65, 83, 68, 69, 81, 84, 70, 82, 32, 340, 341, 342, 256, 257, 258, 259, 262, 263, 264, 265)) {
            add(Binding.Key(code))
        }
        for (code in 49..57) add(Binding.Key(code))
        for (code in 290..301) add(Binding.Key(code))
    }
}

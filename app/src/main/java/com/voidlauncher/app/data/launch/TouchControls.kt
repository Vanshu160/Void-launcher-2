package com.voidlauncher.app.data.launch

import com.voidlauncher.app.data.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The on-screen control state the game screen and the layout editors share: whether controls are shown, their size and
 * opacity, the active layout, what is held, and where each held joystick is.
 *
 * Layouts are named and shared by every screen. "Default Control Layout" is built in and is used until it is edited.
 * Each layout is saved in AppPrefs as JSON under "touch.layout.<name>", and the saved names and the active name are kept
 * under "touch.names" and "touch.active". The earlier build saved positions per screen shape under
 * "touch.layout.<shape>" and "touch.<shape>.<CONTROL>"; those are read once for the default layout.
 *
 * Settings come from SettingsViewModel. Writes happen on the main thread. Call [reload] once AppPrefs is ready.
 */
object TouchControls {

    /** The name of the built-in layout. It is always in the list and cannot be deleted, only restored. */
    const val DEFAULT_NAME = "Default Control Layout"

    private const val KEY_ACTIVE = "touch.active"
    private const val KEY_NAMES = "touch.names"
    private const val KEY_PREFIX = "touch.layout."

    data class Settings(val enabled: Boolean, val scale: Float, val opacity: Float)

    /** A joystick that is held. The base and the knob are in pixels. */
    data class JoystickState(val centreX: Float, val centreY: Float, val knobX: Float, val knobY: Float)

    private val settingsState = MutableStateFlow(Settings(enabled = true, scale = 1f, opacity = 0.6f))
    val settings: StateFlow<Settings> = settingsState.asStateFlow()

    /** The screen shape the game last reported. It picks the older saved positions for the default layout. */
    private var screenProfile = "unknown"

    private val activeState = MutableStateFlow(DEFAULT_NAME)

    /** The name of the layout the game uses. */
    val activeName: StateFlow<String> = activeState.asStateFlow()

    private val namesState = MutableStateFlow<List<String>>(emptyList())

    /** The saved layouts, not counting the built-in default, in the order they were added. */
    val names: StateFlow<List<String>> = namesState.asStateFlow()

    private val layoutState = MutableStateFlow(defaultLayout())

    /** The active layout. */
    val layout: StateFlow<TouchLayout> = layoutState.asStateFlow()

    private val pressedState = MutableStateFlow<Set<String>>(emptySet())

    /** The ids of buttons that are held, or switched on, and so are shown highlighted. */
    val pressed: StateFlow<Set<String>> = pressedState.asStateFlow()

    private val joystickState = MutableStateFlow<Map<String, JoystickState>>(emptyMap())

    /** The held joysticks, by button id. A joystick that is not held is absent and drawn at its layout position. */
    val joysticks: StateFlow<Map<String, JoystickState>> = joystickState.asStateFlow()

    /** The layout a new install starts with. */
    fun defaultLayout(): TouchLayout = TouchLayouts.mojoDefault()

    /** Reads the saved names and the active layout from AppPrefs. Call once after AppPrefs.init. */
    fun reload() {
        val names = readNames()
        namesState.value = names
        val saved = AppPrefs.string(KEY_ACTIVE)
        val active = if (saved != null && (saved == DEFAULT_NAME || saved in names)) saved else DEFAULT_NAME
        activeState.value = active
        layoutState.value = loadNamed(active)
    }

    /** Called from the game screen when its size is known. Pixels. The default layout may come from older saves. */
    fun setScreenSize(width: Int, height: Int) {
        val profile = TouchGeometry.profileFor(width, height)
        if (profile == screenProfile) return
        screenProfile = profile
        if (activeState.value == DEFAULT_NAME) layoutState.value = loadNamed(DEFAULT_NAME)
    }

    /** Pushes the settings in from SettingsViewModel. [scale] is the size slider, and [opacity] the opacity slider. */
    fun applySettings(enabled: Boolean, scale: Float, opacity: Float) {
        settingsState.value = Settings(
            enabled = enabled,
            scale = scale.coerceIn(0.5f, 1.5f),
            opacity = opacity.coerceIn(0.2f, 1f),
        )
    }

    /** Every layout name, the built-in default first. */
    fun allNames(): List<String> = listOf(DEFAULT_NAME) + namesState.value

    /** Makes [name] the layout the game uses. Names that do not exist are ignored. */
    fun setActive(name: String) {
        if (name != DEFAULT_NAME && name !in namesState.value) return
        activeState.value = name
        AppPrefs.edit { editor -> editor.putString(KEY_ACTIVE, name) }
        layoutState.value = loadNamed(name)
    }

    /** The saved layout called [name], or the built-in default for the default name. */
    fun loadNamed(name: String): TouchLayout {
        val saved = AppPrefs.string(KEY_PREFIX + name)
        if (saved != null) {
            try {
                return TouchLayoutJson.decode(saved)
            } catch (e: TouchLayoutFormatException) {
                // A damaged layout falls back to the older positions or the default, rather than failing.
            }
        }
        return if (name == DEFAULT_NAME) migrateLegacy(screenProfile) else defaultLayout()
    }

    /** Saves [layout] as the active layout. */
    fun saveActive(layout: TouchLayout) {
        saveNamed(activeState.value, layout)
    }

    /** Saves [layout] under [name], adding the name to the list if it is new. */
    fun saveNamed(name: String, layout: TouchLayout) {
        val text = TouchLayoutJson.encode(layout)
        AppPrefs.edit { editor -> editor.putString(KEY_PREFIX + name, text) }
        if (name != DEFAULT_NAME && name !in namesState.value) {
            namesState.value = namesState.value + name
            writeNames(namesState.value)
        }
        if (name == activeState.value) layoutState.value = layout
    }

    /**
     * Saves [layout] as a new layout named after [base]. A taken name gets a number. Returns the name it was saved under.
     */
    fun addLayout(layout: TouchLayout, base: String): String {
        val clean = TouchLayouts.cleanName(base)
        val taken = allNames().toSet()
        var name = clean
        var number = 2
        while (name in taken) {
            name = "$clean $number"
            number++
        }
        saveNamed(name, layout)
        return name
    }

    /** Deletes the saved layout [name]. The default layout cannot be deleted, so it is restored instead. */
    fun deleteLayout(name: String) {
        if (name == DEFAULT_NAME) {
            restoreDefault()
            return
        }
        AppPrefs.edit { editor -> editor.remove(KEY_PREFIX + name) }
        namesState.value = namesState.value - name
        writeNames(namesState.value)
        if (activeState.value == name) setActive(DEFAULT_NAME)
    }

    /** Removes the saved edits to the default layout, and its older positions, so the built-in layout applies again. */
    fun restoreDefault() {
        AppPrefs.edit { editor ->
            editor.remove(KEY_PREFIX + DEFAULT_NAME)
            for (legacy in LEGACY_NAMES.values) editor.remove("touch.$screenProfile.$legacy")
        }
        if (activeState.value == DEFAULT_NAME) layoutState.value = loadNamed(DEFAULT_NAME)
    }

    /** The buttons drawn in the current context. */
    fun visibleButtons(inWorld: Boolean): List<TouchButton> =
        layoutState.value.buttons.filter { if (inWorld) it.inWorld else it.inMenu }

    fun setPressed(buttonId: String, down: Boolean) {
        val current = pressedState.value
        pressedState.value = if (down) current + buttonId else current - buttonId
    }

    fun clearPressed() {
        pressedState.value = emptySet()
    }

    /** Sets the state of a held joystick, or removes it with null. */
    fun setJoystick(buttonId: String, state: JoystickState?) {
        val current = joystickState.value
        joystickState.value = if (state == null) current - buttonId else current + (buttonId to state)
    }

    fun clearJoysticks() {
        joystickState.value = emptyMap()
    }

    private fun readNames(): List<String> {
        val text = AppPrefs.string(KEY_NAMES) ?: return emptyList()
        return text.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != DEFAULT_NAME }
            .distinct()
    }

    private fun writeNames(names: List<String>) {
        AppPrefs.edit { editor -> editor.putString(KEY_NAMES, names.joinToString("\n")) }
    }

    /** Reads the positions the earlier build saved, one per control, into the default layout. */
    private fun migrateLegacy(profile: String): TouchLayout {
        val buttons = defaultLayout().buttons.map { button ->
            val legacy = LEGACY_NAMES[button.id] ?: return@map button
            val position = TouchGeometry.parsePosition(AppPrefs.string("touch.$profile.$legacy")) ?: return@map button
            button.copy(x = position.first, y = position.second, anchorRight = false, anchorBottom = false)
        }
        return TouchLayout(buttons)
    }

    /** Default button ids mapped to the control names the earlier build used in its saved keys. */
    private val LEGACY_NAMES: Map<String, String> = mapOf(
        "joystick" to "JOYSTICK",
        "sprint" to "SPRINT",
        "attack" to "ATTACK",
        "use" to "USE",
        "jump" to "JUMP",
        "sneak" to "SNEAK",
        "inventory" to "INVENTORY",
        "drop" to "DROP",
        "chat" to "CHAT",
        "escape" to "ESCAPE",
    )
}

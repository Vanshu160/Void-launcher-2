package com.voidlauncher.app.data.launch

import com.voidlauncher.app.data.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The on-screen control state the game screen and the layout editor share: whether controls are shown, their size
 * and opacity, the saved layout for the current screen shape, what is held, and where each held joystick is.
 *
 * Each layout is saved in AppPrefs as JSON under "touch.layout.<profile>", where the profile names the screen shape.
 * Layouts saved by the earlier build, under "touch.<profile>.<CONTROL>", are read once if no JSON layout exists.
 * Settings come from SettingsViewModel. Writes happen on the main thread.
 */
object TouchControls {

    data class Settings(val enabled: Boolean, val scale: Float, val opacity: Float)

    /** A joystick that is held. The base and the knob are in pixels. */
    data class JoystickState(val centreX: Float, val centreY: Float, val knobX: Float, val knobY: Float)

    private val settingsState = MutableStateFlow(Settings(enabled = true, scale = 1f, opacity = 0.6f))
    val settings: StateFlow<Settings> = settingsState.asStateFlow()

    private val screenState = MutableStateFlow(Pair(0, 0))

    private val profileState = MutableStateFlow(TouchGeometry.profileFor(0, 0))

    /** The layout key for the current screen shape. */
    val profile: StateFlow<String> = profileState.asStateFlow()

    private val layoutState = MutableStateFlow(defaultLayout())

    /** The layout for the current screen shape. */
    val layout: StateFlow<TouchLayout> = layoutState.asStateFlow()

    private val pressedState = MutableStateFlow<Set<String>>(emptySet())

    /** The ids of buttons that are held, or switched on, and so are shown highlighted. */
    val pressed: StateFlow<Set<String>> = pressedState.asStateFlow()

    private val joystickState = MutableStateFlow<Map<String, JoystickState>>(emptyMap())

    /** The held joysticks, by button id. A joystick that is not held is absent and drawn at its layout position. */
    val joysticks: StateFlow<Map<String, JoystickState>> = joystickState.asStateFlow()

    /** The layout a new install starts with. */
    fun defaultLayout(): TouchLayout = TouchLayouts.mojoDefault()

    /** The saved layout for [profile], or the older saved positions, or the default. */
    fun loadLayout(profile: String): TouchLayout {
        val saved = AppPrefs.string(key(profile))
        if (saved != null) {
            try {
                return TouchLayoutJson.decode(saved)
            } catch (e: TouchLayoutFormatException) {
                // A damaged layout falls back to the older positions and then the default, rather than failing.
            }
        }
        return migrateLegacy(profile)
    }

    /** Called from the game screen and the editor when the screen size is known. Pixels, or dp from the editor. */
    fun setScreenSize(width: Int, height: Int) {
        if (screenState.value == Pair(width, height)) return
        screenState.value = Pair(width, height)
        val profile = TouchGeometry.profileFor(width, height)
        profileState.value = profile
        layoutState.value = loadLayout(profile)
    }

    /** Pushes the settings in from SettingsViewModel. [scale] is the size slider, and [opacity] the opacity slider. */
    fun applySettings(enabled: Boolean, scale: Float, opacity: Float) {
        settingsState.value = Settings(
            enabled = enabled,
            scale = scale.coerceIn(0.5f, 1.5f),
            opacity = opacity.coerceIn(0.2f, 1f),
        )
    }

    /** Saves [layout] for [profile]. */
    fun saveLayout(profile: String, layout: TouchLayout) {
        val text = TouchLayoutJson.encode(layout)
        AppPrefs.edit { editor -> editor.putString(key(profile), text) }
        if (profile == profileState.value) layoutState.value = layout
    }

    /** Removes the saved layout for [profile], including the older positions, so the default applies again. */
    fun resetLayout(profile: String) {
        AppPrefs.edit { editor ->
            editor.remove(key(profile))
            for (legacy in LEGACY_NAMES.values) editor.remove("touch.$profile.$legacy")
        }
        if (profile == profileState.value) layoutState.value = defaultLayout()
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

    private fun key(profile: String): String = "touch.layout.$profile"

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

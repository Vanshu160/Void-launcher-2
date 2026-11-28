package com.voidlauncher.app.data.launch

import com.voidlauncher.app.data.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The on-screen control state the game screen and the layout editor share: whether controls are shown, their size
 * and opacity, the saved layout for the current screen shape, and what is currently held.
 *
 * Layouts are saved in AppPrefs under "touch.<profile>.<CONTROL>" as "x,y". Settings come from SettingsViewModel.
 * Writes happen on the main thread.
 */
object TouchControls {

    data class Settings(val enabled: Boolean, val scale: Float, val opacity: Float)

    private val settingsState = MutableStateFlow(Settings(enabled = true, scale = 1f, opacity = 0.6f))
    val settings: StateFlow<Settings> = settingsState.asStateFlow()

    private val screenState = MutableStateFlow(Pair(0, 0))

    private val profileState = MutableStateFlow(TouchGeometry.profileFor(0, 0))

    /** The layout key for the current screen shape. */
    val profile: StateFlow<String> = profileState.asStateFlow()

    private val layoutState = MutableStateFlow(defaultLayout())

    /** Every control's centre for the current screen shape. */
    val layout: StateFlow<Map<TouchControl, ScreenPosition>> = layoutState.asStateFlow()

    private val pressedState = MutableStateFlow<Set<TouchControl>>(emptySet())

    /** Controls that are currently held or tapped, shown highlighted. */
    val pressed: StateFlow<Set<TouchControl>> = pressedState.asStateFlow()

    private val knobState = MutableStateFlow<ScreenPosition?>(null)

    /** Where the joystick knob is, in pixels, or null when no finger is on the joystick. */
    val knob: StateFlow<ScreenPosition?> = knobState.asStateFlow()

    fun defaultLayout(): Map<TouchControl, ScreenPosition> =
        TouchControl.entries.associateWith { it.defaultX to it.defaultY }

    /** The layout for [profile] as saved, with defaults for any control that has no saved position. */
    fun loadLayout(profile: String): Map<TouchControl, ScreenPosition> = TouchControl.entries.associateWith { control ->
        TouchGeometry.parsePosition(AppPrefs.string(key(profile, control))) ?: (control.defaultX to control.defaultY)
    }

    /** Called from the game screen and the editor when the screen size is known. Pixels. */
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

    /** Saves [positions] for [profile]. Controls missing from the map keep their default position. */
    fun saveLayout(profile: String, positions: Map<TouchControl, ScreenPosition>) {
        AppPrefs.edit { editor ->
            for (control in TouchControl.entries) {
                val position = positions[control] ?: (control.defaultX to control.defaultY)
                editor.putString(key(profile, control), TouchGeometry.formatPosition(position))
            }
        }
        if (profile == profileState.value) {
            layoutState.value = TouchControl.entries.associateWith { positions[it] ?: (it.defaultX to it.defaultY) }
        }
    }

    /** Removes the saved layout for [profile], so every control returns to its default position. */
    fun resetLayout(profile: String) {
        AppPrefs.edit { editor ->
            for (control in TouchControl.entries) editor.remove(key(profile, control))
        }
        if (profile == profileState.value) layoutState.value = defaultLayout()
    }

    /** The controls drawn in the current context. */
    fun visibleControls(inWorld: Boolean): List<TouchControl> = TouchControl.entries.filter { it.isVisible(inWorld) }

    fun setPressed(control: TouchControl, down: Boolean) {
        val current = pressedState.value
        pressedState.value = if (down) current + control else current - control
    }

    fun clearPressed() {
        pressedState.value = emptySet()
    }

    fun setKnob(position: ScreenPosition?) {
        knobState.value = position
    }

    private fun key(profile: String, control: TouchControl): String = "touch.$profile.${control.name}"
}

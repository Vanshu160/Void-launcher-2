package com.voidlauncher.app.data.launch

/**
 * The touch and input options the game screen reads. The settings screen pushes its values here
 * through [update]. Values are read on the main thread, but are volatile so any thread may read them.
 *
 * SettingsViewModel pushes the values here at startup and after every change. They are saved by SettingsStore.
 */
object GameInputSettings {
    /** Multiplier for finger and mouse movement while the cursor is relative. */
    @Volatile
    var sensitivity: Float = 1f
        private set

    /** Touchpad mode: finger movement moves the cursor relatively, and a tap clicks where the cursor is. */
    @Volatile
    var touchpadMode: Boolean = false
        private set

    /** Turns off long-press right-click and two-finger tap right-click. Two-finger drag scrolling stays on. */
    @Volatile
    var gesturesDisabled: Boolean = false
        private set

    fun update(sensitivity: Float, touchpadMode: Boolean, gesturesDisabled: Boolean) {
        this.sensitivity = sensitivity.coerceIn(0.25f, 3f)
        this.touchpadMode = touchpadMode
        this.gesturesDisabled = gesturesDisabled
    }
}

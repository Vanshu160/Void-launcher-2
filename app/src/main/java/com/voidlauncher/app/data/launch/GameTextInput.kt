package com.voidlauncher.app.data.launch

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import git.artdeell.dnbootstrap.glfw.GLFW

/**
 * Soft-keyboard text for the game. The game screen's input view ([com.voidlauncher.app.ui.game.GameTextInputView])
 * gives its input connection to this object.
 *
 * Text (letters, digits, symbols, spaces, non-English scripts and emoji) is committed through
 * [GLFW.sendBulkUnicodeEvent], the same path PojavLauncher uses for soft-keyboard text. Control keys
 * (Backspace, Enter, Tab, Delete and the arrows) are sent as key events, the same way the hardware keyboard
 * sends them. A character key is never sent as both a key and text, so nothing is typed twice.
 *
 * Hardware keyboard events never reach the input view, because [GameInput] consumes them first in the
 * activity's key dispatch. That also prevents duplicates.
 *
 * Composing text (the underlined text some input methods show before a word is committed) is not drawn in
 * the game. Text is sent when the input method commits it.
 *
 * Threading: main thread only.
 */
object GameTextInput {
    private var inputView: View? = null
    private var shown = false

    /** True while the soft keyboard is open for the game. */
    fun isShown(): Boolean = shown

    /**
     * Called when the input view is created. A new view means a new window (for example after rotation), and the
     * keyboard does not survive that, so the open flag is cleared.
     */
    fun attach(view: View) {
        inputView = view
        shown = false
    }

    /** Called when the input view is removed. Closes the keyboard, so no keyboard is left open after the game. */
    fun detach(view: View) {
        if (inputView === view) {
            hide()
            inputView = null
        }
    }

    /** Opens the soft keyboard. The input view takes focus so the input method attaches to it. */
    fun show(context: Context) {
        val view = inputView ?: return
        view.requestFocus()
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        shown = true
    }

    /** Closes the soft keyboard if it is open. */
    fun hide() {
        val view = inputView
        if (view != null) {
            val imm = view.context.getSystemService(InputMethodManager::class.java)
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
        shown = false
    }

    /** Closes the keyboard if it is open. Returns true if it was open, so the caller can consume Back. */
    fun hideIfShown(): Boolean {
        if (!shown) return false
        hide()
        return true
    }

    /** Commits text from the input method. Newlines become Enter, and every other character goes to GLFW as text. */
    fun commitText(text: CharSequence) {
        if (!GameSurfaceBridge.inputReady || text.isEmpty()) return
        val pending = StringBuilder()
        for (ch in text) {
            if (ch == '\n' || ch == '\r') {
                flush(pending)
                sendKey(KeyEvent.KEYCODE_ENTER)
            } else {
                pending.append(ch)
            }
        }
        flush(pending)
    }

    /** Handles a key event from the input method. Character keys are skipped, because [commitText] carries them. */
    fun onImeKeyEvent(event: KeyEvent) {
        if (!GameSurfaceBridge.inputReady) return
        if (event.repeatCount != 0) return
        val code = event.keyCode
        if (!isControlKey(code) && event.unicodeChar != 0) return
        val pressed = event.action == KeyEvent.ACTION_DOWN
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return
        GLFW.sendRawKeyEvent(code, if (pressed) 1 else 0, 0, 0.toChar())
    }

    /** Presses and releases one control key. Used for Enter and Backspace from the input method's editor action and deletion. */
    fun sendKey(keyCode: Int) {
        if (!GameSurfaceBridge.inputReady) return
        GLFW.sendRawKeyEvent(keyCode, 1, 0, 0.toChar())
        GLFW.sendRawKeyEvent(keyCode, 0, 0, 0.toChar())
    }

    private fun flush(pending: StringBuilder) {
        if (pending.isEmpty()) return
        GLFW.sendBulkUnicodeEvent(pending.toString(), 0)
        pending.setLength(0)
    }

    private fun isControlKey(code: Int): Boolean = when (code) {
        KeyEvent.KEYCODE_DEL,
        KeyEvent.KEYCODE_FORWARD_DEL,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_TAB,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_MOVE_HOME,
        KeyEvent.KEYCODE_MOVE_END -> true
        else -> false
    }
}

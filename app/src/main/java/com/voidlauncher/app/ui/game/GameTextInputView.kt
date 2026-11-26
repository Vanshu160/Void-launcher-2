package com.voidlauncher.app.ui.game

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.voidlauncher.app.data.launch.GameTextInput

private const val MAX_DELETE_PRESSES = 64

/**
 * A 1 dp focusable view that the soft keyboard attaches to while the game screen is open. It has no drawing and
 * no touch handling. The game's touches go to the game texture view, so this view keeps focus.
 */
class GameTextInputView(context: Context) : View(context) {
    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        return GameInputConnection(this)
    }
}

/**
 * Routes soft-keyboard input to [GameTextInput]. Composing text is accepted and ignored, because the game shows
 * only committed text. Deleting before the cursor sends Backspace, and the editor action sends Enter.
 */
private class GameInputConnection(view: View) : BaseInputConnection(view, false) {
    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        if (text != null) GameTextInput.commitText(text)
        return true
    }

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean = true

    override fun finishComposingText(): Boolean = true

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        // Some input methods ask for very long deletions. Each one is a real key press, so the count is capped.
        repeat(beforeLength.coerceIn(0, MAX_DELETE_PRESSES)) { GameTextInput.sendKey(KeyEvent.KEYCODE_DEL) }
        return true
    }

    override fun sendKeyEvent(event: KeyEvent?): Boolean {
        if (event != null) GameTextInput.onImeKeyEvent(event)
        return true
    }

    override fun performEditorAction(actionCode: Int): Boolean {
        GameTextInput.sendKey(KeyEvent.KEYCODE_ENTER)
        return true
    }
}

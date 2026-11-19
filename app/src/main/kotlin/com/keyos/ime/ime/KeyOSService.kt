package com.keyos.ime.ime

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.keyos.engine.BanglaPhonetic
import com.keyos.engine.KeyDef
import com.keyos.engine.KeyType
import com.keyos.engine.KeyboardLayout
import com.keyos.engine.Layouts
import com.keyos.engine.ShiftState
import com.keyos.ime.ui.KeyboardView

class KeyOSService : InputMethodService(), KeyboardView.Listener {

    private enum class Mode { LETTERS, SYMBOLS, EXTRAS }

    private var keyboardView: KeyboardView? = null
    private var langIndex = 0
    private var mode = Mode.LETTERS
    private var shift = ShiftState.OFF
    private var passwordField = false
    private var lastShiftTap = 0L
    private var lastSpaceTime = 0L
    private val phoneticBuffer = StringBuilder()

    private val letters: KeyboardLayout
        get() = if (passwordField) Layouts.english else Layouts.all[langIndex]

    private val currentLayout: KeyboardLayout
        get() = when (mode) {
            Mode.LETTERS -> letters
            Mode.SYMBOLS -> Layouts.symbols
            Mode.EXTRAS -> Layouts.bengaliExtras
        }

    override fun onCreateInputView(): View =
        KeyboardView(this).also {
            it.listener = this
            keyboardView = it
            refresh()
        }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        phoneticBuffer.clear()
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        passwordField = when (cls) {
            InputType.TYPE_CLASS_TEXT ->
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
        mode = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE ||
            cls == InputType.TYPE_CLASS_DATETIME
        ) Mode.SYMBOLS else Mode.LETTERS
        shift = ShiftState.OFF
        updateAutoCap()
        refresh()
    }

    override fun onFinishInput() {
        flushComposing()
        super.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // The user moved the cursor away from the composing text: drop our buffer.
        if (phoneticBuffer.isNotEmpty() && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            phoneticBuffer.clear()
            currentInputConnection?.finishComposingText()
        }
    }

    // ---- key handling -------------------------------------------------------------------

    override fun onKey(key: KeyDef) {
        when (key.type) {
            KeyType.CHAR -> onChar(key.textFor(shift))
            KeyType.SPACE -> onSpace()
            KeyType.BACKSPACE -> onBackspace()
            KeyType.ENTER -> onEnter()
            KeyType.SHIFT -> onShift()
            KeyType.SYMBOLS -> { flushComposing(); mode = Mode.SYMBOLS; refresh() }
            KeyType.ALPHA -> { mode = Mode.LETTERS; refresh() }
            KeyType.EXTRAS -> { flushComposing(); mode = if (mode == Mode.EXTRAS) Mode.LETTERS else Mode.EXTRAS; refresh() }
            KeyType.LANGUAGE -> {
                flushComposing()
                langIndex = (langIndex + 1) % Layouts.all.size
                mode = Mode.LETTERS
                shift = ShiftState.OFF
                updateAutoCap()
                refresh()
            }
        }
    }

    private fun onChar(text: String) {
        val ic = currentInputConnection ?: return
        val isLatin = text.length == 1 && (text[0] in 'a'..'z' || text[0] in 'A'..'Z')
        if (mode == Mode.LETTERS && letters.phonetic && isLatin) {
            phoneticBuffer.append(text)
            ic.setComposingText(BanglaPhonetic.transliterate(phoneticBuffer.toString()), 1)
        } else {
            flushComposing()
            ic.commitText(text, 1)
        }
        if (shift == ShiftState.ONCE) {
            shift = ShiftState.OFF
            refresh()
        }
    }

    private fun onSpace() {
        val ic = currentInputConnection ?: return
        flushComposing()
        val now = SystemClock.uptimeMillis()
        if (!passwordField && mode == Mode.LETTERS && now - lastSpaceTime < DOUBLE_SPACE_MS) {
            val before = ic.getTextBeforeCursor(2, 0)
            if (before != null && before.length == 2 && before[1] == ' ' &&
                !before[0].isWhitespace() && before[0] !in ".!?।,;:"
            ) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(1, 0)
                ic.commitText(letters.sentenceEnd, 1)
                ic.endBatchEdit()
                lastSpaceTime = 0L
                updateAutoCap()
                refresh()
                return
            }
        }
        ic.commitText(" ", 1)
        lastSpaceTime = now
        updateAutoCap()
        refresh()
    }

    private fun onBackspace() {
        val ic = currentInputConnection ?: return
        if (phoneticBuffer.isNotEmpty()) {
            phoneticBuffer.deleteCharAt(phoneticBuffer.length - 1)
            if (phoneticBuffer.isEmpty()) ic.commitText("", 1)
            else ic.setComposingText(BanglaPhonetic.transliterate(phoneticBuffer.toString()), 1)
            return
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    private fun onEnter() {
        flushComposing()
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noEnterAction = info != null && (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (noEnterAction || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        } else {
            currentInputConnection?.performEditorAction(action)
        }
        updateAutoCap()
        refresh()
    }

    private fun onShift() {
        val now = SystemClock.uptimeMillis()
        shift = when {
            shift == ShiftState.CAPS_LOCK -> ShiftState.OFF
            shift != ShiftState.OFF && now - lastShiftTap < DOUBLE_TAP_MS -> ShiftState.CAPS_LOCK
            shift == ShiftState.OFF -> ShiftState.ONCE
            else -> ShiftState.OFF
        }
        lastShiftTap = now
        refresh()
    }

    // ---- helpers ------------------------------------------------------------------------

    private fun flushComposing() {
        if (phoneticBuffer.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
            phoneticBuffer.clear()
        }
    }

    /** Capitalise automatically at sentence starts (English layout only). */
    private fun updateAutoCap() {
        if (shift == ShiftState.CAPS_LOCK) return
        val info = currentInputEditorInfo
        if (letters.id != "en" || info == null || info.inputType == InputType.TYPE_NULL) {
            shift = ShiftState.OFF
            return
        }
        val caps = currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0
        shift = if (caps != 0) ShiftState.ONCE else ShiftState.OFF
    }

    private fun refresh() {
        keyboardView?.setKeyboard(currentLayout, shift)
    }

    private companion object {
        const val DOUBLE_SPACE_MS = 400L
        const val DOUBLE_TAP_MS = 350L
    }
}

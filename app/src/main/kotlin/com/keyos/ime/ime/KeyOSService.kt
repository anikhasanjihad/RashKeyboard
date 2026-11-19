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
import com.keyos.engine.WordPredictor
import com.keyos.ime.ui.KeyboardView

class KeyOSService : InputMethodService(), KeyboardView.Listener {

    private enum class Mode { LETTERS, SYMBOLS, EXTRAS }

    private var keyboardView: KeyboardView? = null
    private var langIndex = 0
    private var mode = Mode.LETTERS
    private var shift = ShiftState.OFF
    private var passwordField = false
    private var suggestionsEnabled = true
    private var lastShiftTap = 0L
    private var lastSpaceTime = 0L

    /** Raw letters of the word being typed (shown as composing text). */
    private val composing = StringBuilder()
    private var currentSuggestions: List<String> = emptyList()
    private lateinit var predictor: WordPredictor

    private val letters: KeyboardLayout
        get() = if (passwordField) Layouts.english else Layouts.all[langIndex]

    private val currentLayout: KeyboardLayout
        get() = when (mode) {
            Mode.LETTERS -> letters
            Mode.SYMBOLS -> Layouts.symbols
            Mode.EXTRAS -> Layouts.bengaliExtras
        }

    override fun onCreate() {
        super.onCreate()
        predictor = WordPredictor()
        WordStore.load(this, predictor)
    }

    override fun onDestroy() {
        WordStore.save(this, predictor)
        super.onDestroy()
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
        composing.clear()
        showSuggestions(emptyList())
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
        val noSuggestionsFlag = cls == InputType.TYPE_CLASS_TEXT &&
            (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        val emailOrUri = cls == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_URI
            )
        suggestionsEnabled = !passwordField && !noSuggestionsFlag && !emailOrUri
        mode = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE ||
            cls == InputType.TYPE_CLASS_DATETIME
        ) Mode.SYMBOLS else Mode.LETTERS
        shift = ShiftState.OFF
        updateAutoCap()
        refresh()
    }

    override fun onFinishInput() {
        flushComposing()
        WordStore.save(this, predictor)
        super.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // Cursor left the word we are composing: forget it (do not learn it).
        if (composing.isNotEmpty() && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            composing.clear()
            currentInputConnection?.finishComposingText()
            showSuggestions(emptyList())
        }
    }

    // ---- KeyboardView.Listener ----------------------------------------------------------

    override fun onKey(key: KeyDef) {
        when (key.type) {
            KeyType.CHAR -> onChar(key.textFor(shift))
            KeyType.SPACE -> onSpace()
            KeyType.BACKSPACE -> onBackspace()
            KeyType.ENTER -> onEnter()
            KeyType.SHIFT -> onShift()
            KeyType.SYMBOLS -> { flushComposing(); mode = Mode.SYMBOLS; refresh() }
            KeyType.ALPHA -> { mode = Mode.LETTERS; refresh() }
            KeyType.EXTRAS -> {
                flushComposing()
                mode = if (mode == Mode.EXTRAS) Mode.LETTERS else Mode.EXTRAS
                refresh()
            }
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

    /** A character picked from the long-press popup. */
    override fun onText(text: String) {
        val ic = currentInputConnection ?: return
        flushComposing()
        ic.commitText(text, 1)
        afterTyped()
    }

    override fun onCursorMove(direction: Int) {
        flushComposing()
        sendDownUpKeyEvents(if (direction < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
    }

    override fun onSuggestion(index: Int) {
        val ic = currentInputConnection ?: return
        val word = currentSuggestions.getOrNull(index) ?: return
        ic.commitText("$word ", 1) // replaces the composing word
        if (!letters.phonetic) predictor.learn(word)
        composing.clear()
        lastSpaceTime = 0L
        showSuggestions(emptyList())
        updateAutoCap()
        refresh()
    }

    // ---- typing -------------------------------------------------------------------------

    private fun usesComposing(): Boolean =
        mode == Mode.LETTERS && !passwordField &&
            (letters.phonetic || (letters.id == "en" && suggestionsEnabled))

    private fun display(): String =
        if (letters.phonetic) BanglaPhonetic.transliterate(composing.toString()) else composing.toString()

    private fun onChar(text: String) {
        val ic = currentInputConnection ?: return
        val isLatinLetter = text.length == 1 && (text[0] in 'a'..'z' || text[0] in 'A'..'Z')
        if (usesComposing() && isLatinLetter) {
            composing.append(text)
            ic.setComposingText(display(), 1)
            if (!letters.phonetic) updateSuggestions()
        } else {
            flushComposing()
            ic.commitText(text, 1)
        }
        afterTyped()
    }

    private fun afterTyped() {
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
        if (composing.isNotEmpty()) {
            composing.deleteCharAt(composing.length - 1)
            if (composing.isEmpty()) {
                ic.commitText("", 1)
                showSuggestions(emptyList())
            } else {
                ic.setComposingText(display(), 1)
                if (!letters.phonetic) updateSuggestions()
            }
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

    /** Ends the composing word (learning it for English) and clears suggestions. */
    private fun flushComposing() {
        if (composing.isEmpty()) return
        if (!letters.phonetic && suggestionsEnabled && !passwordField) predictor.learn(composing.toString())
        currentInputConnection?.finishComposingText()
        composing.clear()
        showSuggestions(emptyList())
    }

    private fun updateSuggestions() {
        val typed = composing.toString()
        showSuggestions(listOf(typed) + predictor.suggest(typed, 2))
    }

    private fun showSuggestions(list: List<String>) {
        currentSuggestions = list
        keyboardView?.setSuggestions(list)
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

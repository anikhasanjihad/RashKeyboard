package com.keyos.engine

enum class KeyType { CHAR, SHIFT, BACKSPACE, ENTER, SPACE, SYMBOLS, ALPHA, EXTRAS, LANGUAGE }

enum class ShiftState { OFF, ONCE, CAPS_LOCK }

/** One key. [label] is what is typed/shown normally, [shifted] when shift is active. */
data class KeyDef(
    val type: KeyType,
    val label: String = "",
    val shifted: String = label,
    val weight: Float = 1f,
) {
    fun textFor(shift: ShiftState): String = if (shift == ShiftState.OFF) label else shifted
}

/** A row of keys. [sidePadding] is empty space on each side, in key-width units. */
data class KeyRow(val keys: List<KeyDef>, val sidePadding: Float = 0f) {
    val totalUnits: Float get() = keys.sumOf { it.weight.toDouble() }.toFloat() + 2 * sidePadding
}

data class KeyboardLayout(
    val id: String,
    val displayName: String,
    val rows: List<KeyRow>,
    /** true = Latin keys are transliterated to Bengali through [BanglaPhonetic]. */
    val phonetic: Boolean = false,
    /** What a double-space inserts (". " or "। "). */
    val sentenceEnd: String = ". ",
)

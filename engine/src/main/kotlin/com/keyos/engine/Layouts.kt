package com.keyos.engine

import com.keyos.engine.KeyType.*

/**
 * Built-in layouts (Phase 1): English QWERTY, Bengali "Bijoy-style" (traditional) and
 * Bengali phonetic (Avro-style). All layouts have exactly 5 rows so keyboard height never jumps.
 */
object Layouts {

    private val bnDigits = listOf("১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯", "০")
    private val enDigits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    private fun split(s: String) = s.map { it.toString() }

    private fun keys(
        normal: List<String>,
        shifted: List<String> = normal.map { it.uppercase() },
        alts: Map<String, String> = emptyMap(),
    ) = normal.zip(shifted).map { (n, s) -> KeyDef(CHAR, n, s, alts = alts[n]?.let { split(it) } ?: emptyList()) }

    private fun plain(items: List<String>, alts: Map<String, String> = emptyMap()) =
        items.map { KeyDef(CHAR, it, it, alts = alts[it]?.let { a -> split(a) } ?: emptyList()) }

    private fun digitRow(d: List<String>, alts: Map<String, String> = emptyMap()) = KeyRow(plain(d, alts))

    private val enAlts = mapOf(
        "a" to "àáâäæãåā", "c" to "çćč", "e" to "èéêëēėę", "i" to "îïíīįì", "l" to "ł",
        "n" to "ñń", "o" to "ôöòóœøōõ", "s" to "ßśš", "u" to "ûüùúū", "y" to "ÿý", "z" to "žźż",
    )
    private val enDigitAlts = mapOf("0" to "°ⁿ∅", "1" to "¹½⅓¼", "2" to "²⅔", "3" to "³¾", "4" to "⁴")
    private val bnDigitAlts = mapOf(
        "১" to "1", "২" to "2", "৩" to "3", "৪" to "4", "৫" to "5",
        "৬" to "6", "৭" to "7", "৮" to "8", "৯" to "9", "০" to "0",
    )
    private val punctAlts = split("…!?:;'\"-_()")
    private val bnPunctAlts = split("!?:;'\"-.,")
    private val symbolAlts = mapOf(
        "-" to "–—•", "\"" to "“”„«»", "'" to "‘’‚‹›", "?" to "¿", "!" to "¡", "%" to "‰",
        "=" to "≠≈", "৳" to "$€£¥₹¢", "." to "…",
    )

    private fun shiftBackspaceRow(middle: List<KeyDef>) = KeyRow(
        listOf(KeyDef(SHIFT, weight = 1.5f)) + middle + KeyDef(BACKSPACE, weight = 1.5f)
    )

    val english = KeyboardLayout(
        id = "en",
        displayName = "English",
        rows = listOf(
            digitRow(enDigits, enDigitAlts),
            KeyRow(keys(split("qwertyuiop"), alts = enAlts)),
            KeyRow(keys(split("asdfghjkl"), alts = enAlts), sidePadding = 0.5f),
            shiftBackspaceRow(keys(split("zxcvbnm"), alts = enAlts)),
            KeyRow(
                listOf(
                    KeyDef(SYMBOLS, weight = 1.5f),
                    KeyDef(LANGUAGE),
                    KeyDef(CHAR, ",", ","),
                    KeyDef(SPACE, "English", weight = 4f),
                    KeyDef(CHAR, ".", ".", alts = punctAlts),
                    KeyDef(ENTER, weight = 1.5f),
                )
            ),
        ),
    )

    // Bijoy-style positions (traditional Bangla layout). Verify on a device against a reference.
    private val bijoyRow1 = listOf("ঙ", "য", "ড", "প", "ট", "চ", "জ", "হ", "গ", "ড়")
    private val bijoyRow1S = listOf("ং", "য়", "ঢ", "ফ", "ঠ", "ছ", "ঝ", "ঞ", "ঘ", "ঢ়")
    private val bijoyRow2 = listOf("ৃ", "ু", "ি", "া", "্", "ব", "ক", "ত", "দ")
    private val bijoyRow2S = listOf("র্", "ূ", "ী", "অ", "।", "ভ", "খ", "থ", "ধ")
    private val bijoyRow3 = listOf("্র", "ও", "ে", "র", "ন", "স", "ম")
    private val bijoyRow3S = listOf("ঁ", "ৈ", "ৌ", "ল", "ণ", "ষ", "শ")

    val bengaliBijoy = KeyboardLayout(
        id = "bn_bijoy",
        displayName = "বাংলা (বিজয়-স্টাইল)",
        sentenceEnd = "। ",
        rows = listOf(
            digitRow(bnDigits, bnDigitAlts),
            KeyRow(keys(bijoyRow1, bijoyRow1S)),
            KeyRow(keys(bijoyRow2, bijoyRow2S), sidePadding = 0.5f),
            shiftBackspaceRow(keys(bijoyRow3, bijoyRow3S)),
            KeyRow(
                listOf(
                    KeyDef(SYMBOLS, weight = 1.5f),
                    KeyDef(LANGUAGE),
                    KeyDef(EXTRAS),
                    KeyDef(SPACE, "বাংলা", weight = 4f),
                    KeyDef(CHAR, "।", "।", alts = bnPunctAlts),
                    KeyDef(ENTER, weight = 1.5f),
                )
            ),
        ),
    )

    val bengaliPhonetic = KeyboardLayout(
        id = "bn_phonetic",
        displayName = "বাংলা (ফোনেটিক)",
        sentenceEnd = "। ",
        phonetic = true,
        rows = listOf(
            digitRow(bnDigits, bnDigitAlts),
            KeyRow(keys(split("qwertyuiop"))),
            KeyRow(keys(split("asdfghjkl")), sidePadding = 0.5f),
            shiftBackspaceRow(keys(split("zxcvbnm"))),
            KeyRow(
                listOf(
                    KeyDef(SYMBOLS, weight = 1.5f),
                    KeyDef(LANGUAGE),
                    KeyDef(CHAR, ",", ","),
                    KeyDef(SPACE, "ফোনেটিক", weight = 4f),
                    KeyDef(CHAR, "।", "।", alts = bnPunctAlts),
                    KeyDef(ENTER, weight = 1.5f),
                )
            ),
        ),
    )

    /** Digits, symbols and punctuation. */
    val symbols = KeyboardLayout(
        id = "symbols",
        displayName = "?123",
        rows = listOf(
            digitRow(enDigits),
            KeyRow(plain(split("@#") + "৳" + split("_&-+()/"), symbolAlts)),
            KeyRow(plain(split("*\"':;!?%"), symbolAlts) + KeyDef(BACKSPACE, weight = 2f)),
            KeyRow(plain(split("[]{}<>=~|\\"))),
            KeyRow(
                listOf(
                    KeyDef(ALPHA, "ABC", weight = 1.5f),
                    KeyDef(CHAR, ",", ","),
                    KeyDef(SPACE, "", weight = 5f),
                    KeyDef(CHAR, ".", "."),
                    KeyDef(ENTER, weight = 1.5f),
                )
            ),
        ),
    )

    /** Bengali independent vowels, digits and signs that the Bijoy-style base layer has no room for. */
    val bengaliExtras = KeyboardLayout(
        id = "bn_extras",
        displayName = "বাংলা অতিরিক্ত",
        sentenceEnd = "। ",
        rows = listOf(
            digitRow(bnDigits, bnDigitAlts),
            KeyRow(plain(split("অআইঈউঊঋএঐও"))),
            KeyRow(plain(split("ঔৎংঃঁ্।")) + KeyDef(BACKSPACE, weight = 2f), sidePadding = 0.5f),
            KeyRow(plain(split(".,?!:;-'\"")), sidePadding = 0.5f),
            KeyRow(
                listOf(
                    KeyDef(ALPHA, "কখ", weight = 1.5f),
                    KeyDef(CHAR, ",", ","),
                    KeyDef(SPACE, "", weight = 5f),
                    KeyDef(CHAR, "।", "।", alts = bnPunctAlts),
                    KeyDef(ENTER, weight = 1.5f),
                )
            ),
        ),
    )

    /** Layouts the user can cycle through with the language key. */
    val all: List<KeyboardLayout> = listOf(english, bengaliBijoy, bengaliPhonetic)
}

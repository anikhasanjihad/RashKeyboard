package com.keyos.engine

/**
 * Avro-style Bengali phonetic transliteration (Latin -> Bengali Unicode).
 * Pure function, no Android dependencies, unit-tested.
 *
 * Rules: longest match wins; consonant after consonant inserts hasanta (conjunct);
 * vowel after consonant becomes a kar (vowel sign); "o" after a consonant is the inherent
 * vowel (no sign) and ends the conjunct, so "boi" -> বই and "kok" -> কক.
 */
object BanglaPhonetic {

    private const val HASANTA = "্"

    private class Vowel(val independent: String, val kar: String)

    private val consonants = mapOf(
        "k" to "ক", "kh" to "খ", "g" to "গ", "gh" to "ঘ", "Ng" to "ঙ", "NG" to "ঞ",
        "c" to "চ", "ch" to "চ", "chh" to "ছ", "j" to "জ", "jh" to "ঝ",
        "T" to "ট", "Th" to "ঠ", "D" to "ড", "Dh" to "ঢ", "N" to "ণ",
        "t" to "ত", "th" to "থ", "d" to "দ", "dh" to "ধ", "n" to "ন",
        "p" to "প", "ph" to "ফ", "f" to "ফ", "b" to "ব", "bh" to "ভ", "v" to "ভ", "m" to "ম",
        "z" to "য", "r" to "র", "l" to "ল", "sh" to "শ", "S" to "শ", "Sh" to "ষ", "s" to "স", "h" to "হ",
        "R" to "ড়", "Rh" to "ঢ়", "y" to "য়", "Y" to "য়", "q" to "ক", "x" to "ক্স",
    )

    private val vowels = mapOf(
        "a" to Vowel("আ", "া"), "aa" to Vowel("আ", "া"),
        "i" to Vowel("ই", "ি"), "ii" to Vowel("ঈ", "ী"), "I" to Vowel("ঈ", "ী"),
        "u" to Vowel("উ", "ু"), "uu" to Vowel("ঊ", "ূ"), "U" to Vowel("ঊ", "ূ"),
        "e" to Vowel("এ", "ে"),
        "o" to Vowel("অ", ""),
        "O" to Vowel("ও", "ো"),
        "OI" to Vowel("ঐ", "ৈ"), "OU" to Vowel("ঔ", "ৌ"),
        "rri" to Vowel("ঋ", "ৃ"),
    )

    private val specials = mapOf(
        "ng" to "ং", ":" to "ঃ", "^" to "ঁ", "." to "।", ",," to HASANTA, "t``" to "ৎ",
    )

    private const val MAX_TOKEN = 3

    fun transliterate(input: String): String {
        val out = StringBuilder()
        var afterConsonant = false
        var i = 0
        while (i < input.length) {
            var matched = false
            for (len in minOf(MAX_TOKEN, input.length - i) downTo 1) {
                val tok = input.substring(i, i + len)
                val sp = specials[tok]
                if (sp != null) {
                    out.append(sp); afterConsonant = false
                } else {
                    val c = consonants[tok]
                    if (c != null) {
                        if (afterConsonant) out.append(HASANTA)
                        out.append(c); afterConsonant = true
                    } else {
                        val v = vowels[tok] ?: continue
                        out.append(if (afterConsonant) v.kar else v.independent)
                        afterConsonant = false
                    }
                }
                i += len
                matched = true
                break
            }
            if (!matched) {
                out.append(input[i]); afterConsonant = false; i++
            }
        }
        return out.toString()
    }
}

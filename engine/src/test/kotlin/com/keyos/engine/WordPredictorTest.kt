package com.keyos.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WordPredictorTest {
    @Test fun completesPrefix() {
        val s = WordPredictor().suggest("th", 3)
        assertEquals(3, s.size)
        assertTrue(s.all { it.startsWith("th") })
    }

    @Test fun excludesExactWord() = assertTrue("the" !in WordPredictor().suggest("the", 5))

    @Test fun keepsCapitalisation() {
        assertTrue(WordPredictor().suggest("Hel", 3).all { it[0].isUpperCase() })
        assertTrue(WordPredictor().suggest("HEL", 3).all { it == it.uppercase() })
    }

    @Test fun learnsUserWordsAfterTwoUses() {
        val p = WordPredictor()
        p.learn("thailand")
        assertTrue("thailand" !in p.suggest("thai"))
        p.learn("thailand")
        assertEquals("thailand", p.suggest("thai").first())
    }

    @Test fun exportImportRoundTrip() {
        val p = WordPredictor(); repeat(3) { p.learn("zebraxyz") }
        val q = WordPredictor(); q.importUser(p.exportUser())
        assertEquals("zebraxyz", q.suggest("zebra").first())
    }

    @Test fun ignoresDigitsAndTooShort() {
        val p = WordPredictor(); repeat(3) { p.learn("a1b2c3"); p.learn("x") }
        assertTrue(p.exportUser().isEmpty())
    }
}

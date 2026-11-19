package com.keyos.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class BanglaPhoneticTest {
    private fun t(s: String) = BanglaPhonetic.transliterate(s)

    @Test fun ami() = assertEquals("আমি", t("ami"))
    @Test fun tumi() = assertEquals("তুমি", t("tumi"))
    @Test fun bangla() = assertEquals("বাংলা", t("bangla"))
    @Test fun boi() = assertEquals("বই", t("boi"))
    @Test fun bhalobasi() = assertEquals("ভালোবাসি", t("bhalObasi"))
    @Test fun conjunct() = assertEquals("ক্ষ", t("kSh"))
    @Test fun hasantaBetweenConsonants() = assertEquals("ক্ত", t("kt"))
    @Test fun inherentOBreaksConjunct() = assertEquals("কত", t("kot"))
    @Test fun danda() = assertEquals("আমি।", t("ami."))
    @Test fun unknownPassesThrough() = assertEquals("১২৩", t("১২৩"))
    @Test fun empty() = assertEquals("", t(""))
}

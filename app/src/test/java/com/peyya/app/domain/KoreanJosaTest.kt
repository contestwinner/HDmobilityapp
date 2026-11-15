package com.peyya.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class KoreanJosaTest {
    @Test
    fun hangul_batchimDecides() {
        assertEquals("가족과", KoreanJosa.gwaWa("가족"))
        assertEquals("아빠와", KoreanJosa.gwaWa("아빠"))
    }

    @Test
    fun latinAndDigits_followPronunciation() {
        assertEquals("패밀리 SUV와", KoreanJosa.gwaWa("패밀리 SUV"))
        assertEquals("EV9와", KoreanJosa.gwaWa("EV9"))
        assertEquals("아이오닉 6과", KoreanJosa.gwaWa("아이오닉 6"))
        assertEquals("싼타페 HEV와", KoreanJosa.gwaWa("싼타페 HEV"))
    }

    @Test
    fun blank_isSafe() {
        assertEquals("와", KoreanJosa.gwaWa(""))
    }

    @Test
    fun euroRo_handlesRieulAndVowel() {
        assertEquals("가족으로", KoreanJosa.euroRo("가족"))
        assertEquals("서울로", KoreanJosa.euroRo("서울"))
        assertEquals("바다로", KoreanJosa.euroRo("바다"))
        assertEquals("3곳으로", KoreanJosa.euroRo("3곳"))
    }
}

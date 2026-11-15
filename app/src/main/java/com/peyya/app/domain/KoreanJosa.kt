package com.peyya.app.domain

/** Picks 과/와 for a user-provided word (가족명·차량 별명 등) so copy reads naturally. */
object KoreanJosa {
    private const val HANGUL_START = 0xAC00
    private const val HANGUL_END = 0xD7A3
    private const val JONG_COUNT = 28
    private const val RIEUL_INDEX = 8
    private const val CONSONANT_SOUND_LATIN = "lmnLMN"
    private const val CONSONANT_SOUND_DIGIT = "013678"

    fun gwaWa(word: String): String = word + if (endsWithBatchim(word)) "과" else "와"

    /** 으로/로: 받침이 없거나 ㄹ받침이면 "로". */
    fun euroRo(word: String): String = word + if (endsWithBatchim(word) && !endsWithRieul(word)) "으로" else "로"

    private fun endsWithRieul(word: String): Boolean {
        val last = word.trimEnd().lastOrNull() ?: return false
        if (last.code in HANGUL_START..HANGUL_END) return (last.code - HANGUL_START) % JONG_COUNT == RIEUL_INDEX
        return last == 'l' || last == 'L' || last == '1' || last == '7' || last == '8'
    }

    private fun endsWithBatchim(word: String): Boolean {
        val last = word.trimEnd().lastOrNull() ?: return false
        return when {
            last.code in HANGUL_START..HANGUL_END -> (last.code - HANGUL_START) % JONG_COUNT != 0
            last in CONSONANT_SOUND_LATIN -> true
            last in CONSONANT_SOUND_DIGIT -> true
            else -> false
        }
    }
}

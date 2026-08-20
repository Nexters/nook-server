package org.every.nook.api.application.place

internal fun String.singleHangulOcrAliases(): List<String> {
    if (length != 1 || single() !in '\uAC00'..'\uD7A3') return emptyList()
    val syllableIndex = single().code - HANGUL_SYLLABLE_BASE
    val finalConsonantIndex = syllableIndex % HANGUL_FINAL_CONSONANT_COUNT
    return HANGUL_OCR_FINAL_CONSONANT_ALIASES[finalConsonantIndex].orEmpty().map { aliasFinalConsonant ->
        (HANGUL_SYLLABLE_BASE + syllableIndex - finalConsonantIndex + aliasFinalConsonant).toChar().toString()
    }
}

private const val HANGUL_SYLLABLE_BASE = 0xAC00
private const val HANGUL_FINAL_CONSONANT_COUNT = 28
private val HANGUL_OCR_FINAL_CONSONANT_ALIASES = mapOf(16 to listOf(17), 17 to listOf(16))

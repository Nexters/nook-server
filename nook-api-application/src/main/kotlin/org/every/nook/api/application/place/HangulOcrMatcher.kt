package org.every.nook.api.application.place

internal object HangulOcrMatcher {
    fun isNearMatch(left: String, right: String): Boolean {
        if (left.none { it.isHangulSyllable() } || right.none { it.isHangulSyllable() }) return false
        val leftJamo = left.decomposeHangul()
        val rightJamo = right.decomposeHangul()
        val maxLength = maxOf(leftJamo.length, rightJamo.length)
        val distance = leftJamo.editDistance(rightJamo)
        return distance <= MAX_EDIT_DISTANCE && distance * MAX_ERROR_RATIO <= maxLength
    }

    private fun String.decomposeHangul(): String = buildString {
        this@decomposeHangul.forEach { character -> appendDecomposed(character) }
    }

    private fun StringBuilder.appendDecomposed(character: Char) {
        if (!character.isHangulSyllable()) {
            append(character)
            return
        }
        val syllableIndex = character.code - HANGUL_SYLLABLE_START
        append(encodedJamo(syllableIndex / JUNGSEONG_COUNT / JONGSEONG_COUNT))
        append(encodedJamo(CHOSEONG_COUNT + syllableIndex / JONGSEONG_COUNT % JUNGSEONG_COUNT))
        val jongseong = syllableIndex % JONGSEONG_COUNT
        if (jongseong != 0) {
            append(encodedJamo(CHOSEONG_COUNT + JUNGSEONG_COUNT + jongseong))
        }
    }

    private fun String.editDistance(other: String): Int {
        val distances = IntArray(other.length + 1) { it }
        forEachIndexed { leftIndex, left ->
            var previous = distances[0]
            distances[0] = leftIndex + 1
            other.forEachIndexed { rightIndex, right ->
                val replaced = previous + if (left == right) 0 else 1
                previous = distances[rightIndex + 1]
                distances[rightIndex + 1] = minOf(
                    distances[rightIndex + 1] + 1,
                    distances[rightIndex] + 1,
                    replaced,
                )
            }
        }
        return distances.last()
    }

    private fun encodedJamo(index: Int): Char = (JAMO_ENCODING_START + index).toChar()

    private fun Char.isHangulSyllable(): Boolean = code in HANGUL_SYLLABLE_START..HANGUL_SYLLABLE_END

    private const val MAX_EDIT_DISTANCE = 2
    private const val MAX_ERROR_RATIO = 3
    private const val HANGUL_SYLLABLE_START = 0xAC00
    private const val HANGUL_SYLLABLE_END = 0xD7A3
    private const val CHOSEONG_COUNT = 19
    private const val JUNGSEONG_COUNT = 21
    private const val JONGSEONG_COUNT = 28
    private const val JAMO_ENCODING_START = 0xE000
}

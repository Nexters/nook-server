package org.every.nook.api.application.place

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaceSourceMediaSequenceTest {
    @Test
    fun `keeps the original clue position after an unresolved place and skips a cover`() {
        val clue = PlaceClue("네 번째 장소", null, listOf("네 번째 장소"))

        val sequence = clue.sourceMediaSequence(
            clueSequence = 3,
            imageCount = 7,
            sourcePlaceCount = 6,
            useEvidenceImageSequence = false,
        )

        assertEquals(4, sequence)
    }

    @Test
    fun `uses the exact evidence image for an OCR clue`() {
        val clue = PlaceClue(
            name = "여섯 번째 장소",
            region = null,
            queries = listOf("여섯 번째 장소"),
            evidence = listOf(PlaceClueEvidence(imageIndex = 7, evidenceText = "여섯 번째 장소")),
        )

        val sequence = clue.sourceMediaSequence(
            clueSequence = 0,
            imageCount = 7,
            sourcePlaceCount = 6,
            useEvidenceImageSequence = true,
        )

        assertEquals(6, sequence)
    }

    @Test
    fun `allows post media when one clue is grounded in one evidence image`() {
        val clue = PlaceClue(
            name = "누크 카페",
            region = null,
            queries = listOf("누크 카페"),
            evidence = listOf(PlaceClueEvidence(2, "누크 카페 서울 종로구 창경궁로 1")),
        )

        assertTrue(clue.hasExclusiveGroundedImageEvidence(listOf(clue)))
    }

    @Test
    fun `rejects post media when one image contains multiple place clues`() {
        val first = PlaceClue("누크 카페", null, listOf("누크 카페"), listOf(PlaceClueEvidence(2, "누크 카페")))
        val second = PlaceClue("다른 카페", null, listOf("다른 카페"), listOf(PlaceClueEvidence(2, "다른 카페")))

        assertFalse(first.hasExclusiveGroundedImageEvidence(listOf(first, second)))
    }
}

package org.every.nook.api.application.place

import kotlin.test.Test
import kotlin.test.assertEquals

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
}

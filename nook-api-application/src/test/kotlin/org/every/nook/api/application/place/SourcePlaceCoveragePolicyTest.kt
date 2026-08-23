package org.every.nook.api.application.place

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourcePlaceCoveragePolicyTest {
    @Test
    fun `finds a numbered item omitted when two source items share one username`() {
        val body = """
            1. Vintage Universe @vintage_unvs
            2. Segra Vintage @vintage_unvs
            3. 바버샵 @barbershop
        """.trimIndent()
        val clues = listOf(
            PlaceClue("Vintage Universe", null, listOf("@vintage_unvs")),
            PlaceClue("바버샵", null, listOf("@barbershop")),
        )

        val result = SourcePlaceCoveragePolicy().evaluate(SourcePlaceCoveragePolicy.Context(body, clues)).result

        assertEquals(3, result.expectedPlaceCount)
        assertEquals(listOf("Segra Vintage"), result.missingItems.map { it.name })
        assertEquals(UnresolvedPlaceClue.Type.NOT_EXTRACTED, result.unresolvedClues().single().type)
    }

    @Test
    fun `does not report missing items when distinct clues cover the numbered list`() {
        val body = """
            1. Vintage Universe @vintage_unvs
            2. Segra Vintage @vintage_unvs
        """.trimIndent()
        val clues = listOf(
            PlaceClue("Vintage Universe", null, listOf("@vintage_unvs")),
            PlaceClue("Segra Vintage", null, listOf("@vintage_unvs")),
        )

        val result = SourcePlaceCoveragePolicy().evaluate(SourcePlaceCoveragePolicy.Context(body, clues)).result

        assertEquals(2, result.expectedPlaceCount)
        assertTrue(result.missingItems.isEmpty())
    }

    @Test
    fun `ignores an isolated numbered sentence`() {
        val result = SourcePlaceCoveragePolicy().evaluate(
            SourcePlaceCoveragePolicy.Context("1. 오늘의 추천", emptyList()),
        ).result

        assertNull(result.expectedPlaceCount)
        assertTrue(result.missingItems.isEmpty())
    }
}

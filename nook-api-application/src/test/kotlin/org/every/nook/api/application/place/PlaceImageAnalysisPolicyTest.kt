package org.every.nook.api.application.place

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaceImageAnalysisPolicyTest {
    @Test
    fun `does not require image OCR when text has every expected place clue`() {
        assertFalse(
            requiresImageAnalysis(
                textClueCount = 6,
                textResolvedCount = 4,
                expectedPlaceCount = 6,
            ),
        )
    }

    @Test
    fun `requires image OCR when no text clue resolves to a place`() {
        assertTrue(
            requiresImageAnalysis(
                textClueCount = 6,
                textResolvedCount = 0,
                expectedPlaceCount = 6,
            ),
        )
    }
}

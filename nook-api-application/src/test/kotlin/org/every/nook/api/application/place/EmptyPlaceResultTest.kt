package org.every.nook.api.application.place

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmptyPlaceResultTest {
    @Test
    fun `empty result requires absence of clues expectations and failures`() {
        assertTrue(isNormalEmptyPlaceResult(0, null, null))
        assertTrue(isNormalEmptyPlaceResult(0, 0, null))
        assertFalse(isNormalEmptyPlaceResult(1, null, null))
        assertFalse(isNormalEmptyPlaceResult(0, 2, null))
        assertFalse(isNormalEmptyPlaceResult(0, null, IllegalStateException("OCR failed")))
    }
}

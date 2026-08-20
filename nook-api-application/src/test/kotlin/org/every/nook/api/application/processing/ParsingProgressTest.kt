package org.every.nook.api.application.processing

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ParsingProgressTest {
    @Test
    fun `interpolates only inside the current milestone range`() {
        val startedAt = Instant.parse("2026-08-21T00:00:00Z")
        val progress = ParsingProgress(
            ParsingProgressStage.PLACE_IMAGE_OCR,
            startedAt,
            persistedPercent = 74,
        )

        assertEquals(78, progress.percentAt(startedAt.plusSeconds(13)))
        assertEquals(82, progress.percentAt(startedAt.plusSeconds(60)))
    }

    @Test
    fun `never returns less than the persisted high watermark`() {
        val startedAt = Instant.parse("2026-08-21T00:00:00Z")
        val progress = ParsingProgress(
            ParsingProgressStage.PLACE_TEXT_RESOLUTION,
            startedAt,
            persistedPercent = 72,
        )

        assertEquals(72, progress.percentAt(startedAt.plusSeconds(1)))
    }
}

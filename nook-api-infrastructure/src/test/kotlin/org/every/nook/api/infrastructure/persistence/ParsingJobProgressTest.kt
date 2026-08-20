package org.every.nook.api.infrastructure.persistence

import org.every.nook.api.application.processing.ParsingProgressStage
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobEntity
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ParsingJobProgressTest {
    @Test
    fun `freezes interpolated progress and ignores an earlier retry milestone`() {
        val startedAt = Instant.parse("2026-08-21T00:00:00Z")
        val job = PlaceParsingJobEntity(postId = 1, status = PlaceParsingStatus.PROCESSING)

        job.advanceProgress(ParsingProgressStage.PLACE_IMAGE_OCR, startedAt)
        job.freezeProgress(startedAt.plusSeconds(13))
        val frozenPercent = job.progressPercent
        job.resumeProgress(startedAt.plusSeconds(30))
        job.advanceProgress(ParsingProgressStage.PLACE_TEXT_CLUES, startedAt.plusSeconds(30))

        assertEquals(78, frozenPercent)
        assertEquals(ParsingProgressStage.PLACE_IMAGE_OCR, job.progressStage)
        assertEquals(78, job.progress().percentAt(startedAt.plusSeconds(30)))
    }

    @Test
    fun `advances immediately when an optional milestone is skipped`() {
        val now = Instant.parse("2026-08-21T00:00:00Z")
        val job = PlaceParsingJobEntity(postId = 1, status = PlaceParsingStatus.PROCESSING)

        job.advanceProgress(ParsingProgressStage.PLACE_TEXT_RESOLUTION, now)
        job.advanceProgress(ParsingProgressStage.PLACE_SAVE, now.plusSeconds(1))

        assertEquals(ParsingProgressStage.PLACE_SAVE, job.progressStage)
        assertEquals(93, job.progressPercent)
    }
}

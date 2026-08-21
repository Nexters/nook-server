package org.every.nook.api.application.post.model

import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.PostContentParsingStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class PostProcessingViewTest {
    @Test
    fun `content status takes precedence before place parsing exists`() {
        assertEquals(
            PostProcessingView(PostProcessingStatusView.PROCESSING, PostProcessingStageView.CONTENT),
            PostProcessingView.from(PostContentParsingStatus.PROCESSING, null),
        )
        assertEquals(
            PostProcessingView(PostProcessingStatusView.FAILED, PostProcessingStageView.CONTENT),
            PostProcessingView.from(PostContentParsingStatus.FAILED, PlaceParsingStatus.COMPLETED),
        )
    }

    @Test
    fun `completed content waits for place parsing but remains completed when only place parsing fails`() {
        assertEquals(
            PostProcessingView(PostProcessingStatusView.PENDING, PostProcessingStageView.PLACE),
            PostProcessingView.from(PostContentParsingStatus.COMPLETED, null),
        )
        assertEquals(
            PostProcessingView(PostProcessingStatusView.PROCESSING, PostProcessingStageView.PLACE),
            PostProcessingView.from(PostContentParsingStatus.COMPLETED, PlaceParsingStatus.PROCESSING),
        )
        assertEquals(
            PostProcessingView(PostProcessingStatusView.COMPLETED, null),
            PostProcessingView.from(PostContentParsingStatus.COMPLETED, PlaceParsingStatus.COMPLETED),
        )
        assertEquals(
            PostProcessingView(PostProcessingStatusView.COMPLETED, null),
            PostProcessingView.from(PostContentParsingStatus.COMPLETED, PlaceParsingStatus.FAILED),
        )
    }

    @Test
    fun `processing percent grows within the current stage estimate`() {
        val startedAt = Instant.parse("2026-08-10T00:00:00Z")

        assertEquals(
            35,
            PostProcessingView.from(
                contentStatus = PostContentParsingStatus.PROCESSING,
                placeStatus = null,
                contentStartedAt = startedAt,
                now = startedAt.plusSeconds(30),
            ).processingPercent,
        )
        assertEquals(
            83,
            PostProcessingView.from(
                contentStatus = PostContentParsingStatus.COMPLETED,
                placeStatus = PlaceParsingStatus.PROCESSING,
                placeStartedAt = startedAt,
                now = startedAt.plusSeconds(60),
            ).processingPercent,
        )
    }

    @Test
    fun `processing percent is capped until terminal completion returns 100`() {
        val startedAt = Instant.parse("2026-08-10T00:00:00Z")

        assertEquals(
            45,
            PostProcessingView.from(
                contentStatus = PostContentParsingStatus.PROCESSING,
                placeStatus = null,
                contentStartedAt = startedAt,
                now = startedAt.plusSeconds(300),
            ).processingPercent,
        )
        assertEquals(
            100,
            PostProcessingView.from(
                contentStatus = PostContentParsingStatus.COMPLETED,
                placeStatus = PlaceParsingStatus.COMPLETED,
                now = startedAt,
            ).processingPercent,
        )
    }
}

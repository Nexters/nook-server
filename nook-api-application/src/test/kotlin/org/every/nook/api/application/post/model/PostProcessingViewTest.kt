package org.every.nook.api.application.post.model

import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.PostContentParsingStatus
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
    fun `completed content delegates overall status to place parsing`() {
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
    }
}

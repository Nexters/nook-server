package org.every.nook.api.application.post.model

import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.PostContentParsingStatus

data class PostProcessingView(val status: PostProcessingStatusView, val stage: PostProcessingStageView?) {
    companion object {
        fun from(contentStatus: PostContentParsingStatus, placeStatus: PlaceParsingStatus?): PostProcessingView =
            when (contentStatus) {
                PostContentParsingStatus.PENDING -> PostProcessingView(
                    PostProcessingStatusView.PENDING,
                    PostProcessingStageView.CONTENT,
                )

                PostContentParsingStatus.PROCESSING -> PostProcessingView(
                    PostProcessingStatusView.PROCESSING,
                    PostProcessingStageView.CONTENT,
                )

                PostContentParsingStatus.FAILED -> PostProcessingView(
                    PostProcessingStatusView.FAILED,
                    PostProcessingStageView.CONTENT,
                )

                PostContentParsingStatus.COMPLETED -> fromPlace(placeStatus)
            }

        private fun fromPlace(status: PlaceParsingStatus?): PostProcessingView = when (status) {
            null,
            PlaceParsingStatus.PENDING,
            -> PostProcessingView(PostProcessingStatusView.PENDING, PostProcessingStageView.PLACE)

            PlaceParsingStatus.PROCESSING ->
                PostProcessingView(PostProcessingStatusView.PROCESSING, PostProcessingStageView.PLACE)

            PlaceParsingStatus.COMPLETED -> PostProcessingView(PostProcessingStatusView.COMPLETED, null)

            PlaceParsingStatus.FAILED ->
                PostProcessingView(PostProcessingStatusView.FAILED, PostProcessingStageView.PLACE)
        }
    }
}

enum class PostProcessingStatusView {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
}

enum class PostProcessingStageView {
    CONTENT,
    PLACE,
}

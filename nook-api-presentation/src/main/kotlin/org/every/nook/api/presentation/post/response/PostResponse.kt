package org.every.nook.api.presentation.post.response

import org.every.nook.api.application.post.CreatePostUseCase
import org.every.nook.api.application.post.model.PlaceParsingStatusView

data class PostResponse(val postId: Long, val placeParsingStatus: PlaceParsingStatusView) {
    companion object {
        fun from(result: CreatePostUseCase.Result): PostResponse = PostResponse(
            postId = result.postId,
            placeParsingStatus = result.placeParsingStatus,
        )
    }
}

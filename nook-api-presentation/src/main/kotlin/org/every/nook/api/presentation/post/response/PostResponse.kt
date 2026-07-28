package org.every.nook.api.presentation.post.response

import io.swagger.v3.oas.annotations.media.Schema
import org.every.nook.api.application.post.CreatePostUseCase
import org.every.nook.api.application.post.model.PlaceParsingStatusView

data class PostResponse(
    @field:Schema(description = "저장된 게시물 식별자")
    val postId: Long,
    @field:Schema(description = "게시물 장소 파싱 상태")
    val placeParsingStatus: PlaceParsingStatusView,
) {
    companion object {
        fun from(result: CreatePostUseCase.Result): PostResponse = PostResponse(
            postId = result.postId,
            placeParsingStatus = result.placeParsingStatus,
        )
    }
}

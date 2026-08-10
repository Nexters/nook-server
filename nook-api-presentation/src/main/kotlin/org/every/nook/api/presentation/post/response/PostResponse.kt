package org.every.nook.api.presentation.post.response

import io.swagger.v3.oas.annotations.media.Schema
import org.every.nook.api.application.post.CreatePostUseCase
import org.every.nook.api.application.post.model.PlaceParsingStatusView
import org.every.nook.api.application.post.model.PostProcessingStageView
import org.every.nook.api.application.post.model.PostProcessingStatusView

data class PostResponse(
    @field:Schema(description = "저장된 게시물 식별자")
    val postId: Long,
    @field:Schema(description = "게시물 장소 파싱 상태")
    val placeParsingStatus: PlaceParsingStatusView,
    @field:Schema(description = "게시물 전체 처리 상태")
    val processingStatus: PostProcessingStatusView,
    @field:Schema(description = "현재 처리 단계. 모든 처리가 완료되면 null입니다.", nullable = true)
    val processingStage: PostProcessingStageView?,
    @field:Schema(description = "게시물 처리 진행률. 0부터 100 사이의 정수입니다.")
    val processingPercent: Int,
) {
    companion object {
        fun from(result: CreatePostUseCase.Result): PostResponse = PostResponse(
            postId = result.postId,
            placeParsingStatus = result.placeParsingStatus,
            processingStatus = result.processingStatus,
            processingStage = result.processingStage,
            processingPercent = result.processingPercent,
        )
    }
}

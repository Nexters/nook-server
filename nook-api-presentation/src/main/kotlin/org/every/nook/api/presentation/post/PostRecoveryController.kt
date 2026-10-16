package org.every.nook.api.presentation.post

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.every.nook.api.application.post.GetSavedPostRecoveryUseCase
import org.every.nook.api.application.post.RequestSavedPostRecoveryUseCase
import org.every.nook.api.application.post.SavedPostRecovery
import org.every.nook.api.application.post.model.PostProcessingStageView
import org.every.nook.api.application.post.model.PostProcessingStatusView
import org.every.nook.api.presentation.auth.UserContext
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/posts/{postId}/recovery")
@Tag(name = "Post")
class PostRecoveryController(
    private val getRecovery: GetSavedPostRecoveryUseCase,
    private val requestRecovery: RequestSavedPostRecoveryUseCase,
) {
    @GetMapping
    @Operation(summary = "내 저장 게시물의 복구 처리 상태 조회")
    fun get(@Parameter(hidden = true) userContext: UserContext, @PathVariable postId: Long) =
        ApiResponse.success(RecoveryResponse.from(getRecovery(userContext.userId, postId)))

    @PostMapping
    @Operation(summary = "기존 URL과 아카이브로 실패 게시물 복구 요청; 진행 중이거나 완료된 작업은 중복 실행하지 않습니다")
    fun request(
        @Parameter(hidden = true) userContext: UserContext,
        @PathVariable postId: Long,
    ): ResponseEntity<ApiResponse<RecoveryResponse>> {
        val result = requestRecovery(userContext.userId, postId)
        val status = if (result.processing.status == PostProcessingStatusView.COMPLETED) {
            HttpStatus.OK
        } else {
            HttpStatus.ACCEPTED
        }
        return ResponseEntity.status(status).body(ApiResponse.success(RecoveryResponse.from(result)))
    }

    data class RecoveryResponse(
        val postId: Long,
        val requested: Boolean,
        val processingStatus: PostProcessingStatusView,
        val processingStage: PostProcessingStageView?,
        val processingPercent: Int,
    ) {
        companion object {
            fun from(result: SavedPostRecovery) = RecoveryResponse(
                result.postId,
                result.requested,
                result.processing.status,
                result.processing.stage,
                result.processing.processingPercent,
            )
        }
    }
}

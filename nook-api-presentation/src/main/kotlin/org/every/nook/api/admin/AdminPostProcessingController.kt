package org.every.nook.api.admin

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.every.nook.api.application.admin.AdminActor
import org.every.nook.api.application.admin.ChangePostDisposition
import org.every.nook.api.application.admin.ChangePostDispositionUseCase
import org.every.nook.api.application.admin.ListPostProcessingUseCase
import org.every.nook.api.application.admin.PostProcessingQuery
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/admin/v1/post-processing")
@Tag(name = "Admin Post Processing")
class AdminPostProcessingController(
    private val listPosts: ListPostProcessingUseCase,
    private val changeDisposition: ChangePostDispositionUseCase,
) {
    @GetMapping
    @Operation(summary = "최근 실패와 관리자 재시도 실패, 처리 불가 게시글을 조회합니다")
    fun list(
        @Parameter(description = "ALL, INITIAL_FAILED, RETRY_FAILED, UNPROCESSABLE, ACTIVE 또는 COMPLETED")
        @RequestParam(defaultValue = "INITIAL_FAILED") category: String,
        @Parameter(description = "최근 실패 시각의 시작값, ISO-8601 형식; 생략하면 전체 기간")
        @RequestParam(required = false) failedSince: Instant?,
        @Parameter(description = "1부터 시작하는 페이지 번호")
        @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "페이지당 게시글 수, 1~100")
        @RequestParam(defaultValue = "20") limit: Int,
        @Parameter(description = "특정 게시글 번호, 생략하면 전체 게시글")
        @RequestParam(required = false) postId: Long?,
    ) = ApiResponse.success(listPosts(PostProcessingQuery(category, failedSince, page, limit, postId)))

    @PatchMapping("/{postId}/disposition")
    @Operation(summary = "게시글을 수동으로 처리 불가로 분류하거나 복구 대상에 다시 포함합니다; 변경 사유는 감사 기록에 남습니다")
    fun change(
        @Parameter(description = "운영 상태를 변경할 게시글 번호") @PathVariable postId: Long,
        @RequestBody request: DispositionRequest,
        actor: AdminActor,
    ) = ApiResponse.success(
        changeDisposition(ChangePostDisposition(postId, request.disposition, request.reason, actor)),
    )

    data class DispositionRequest(
        @field:Schema(description = "OPEN: 복구 대상, UNPROCESSABLE: 처리 불가") val disposition: String,
        @field:Schema(description = "운영 상태 변경 사유, 공백 제외 1~500자") val reason: String,
    )
}

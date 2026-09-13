package org.every.nook.api.admin

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.every.nook.api.application.admin.AdminActor
import org.every.nook.api.application.admin.GetAdminPostRecoveryUseCase
import org.every.nook.api.application.admin.ListAdminPostRecoveryUseCase
import org.every.nook.api.application.admin.RetryAdminPostRecoveryUseCase
import org.every.nook.api.application.admin.RetryPostParsingCommand
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/v1/parsing-posts")
@Tag(name = "Admin Parsing Recovery")
class AdminPostRecoveryController(
    private val listPosts: ListAdminPostRecoveryUseCase,
    private val getPost: GetAdminPostRecoveryUseCase,
    private val retryPost: RetryAdminPostRecoveryUseCase,
) {
    @GetMapping
    @Operation(summary = "게시물 단위로 파싱 작업을 묶어 조회합니다; 상태 필터는 게시물 선정에만 적용합니다")
    fun list(
        @RequestParam(required = false) postId: Long?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) beforePostId: Long?,
        @RequestParam(defaultValue = "20") limit: Int,
    ) = ApiResponse.success(listPosts(postId, status, beforePostId, limit))

    @GetMapping("/{postId}")
    @Operation(summary = "한 게시물의 모든 파싱 단계와 복구 진행을 조회합니다")
    fun get(@PathVariable postId: Long) = ApiResponse.success(getPost(postId))

    @PostMapping("/{postId}/retry")
    @Operation(summary = "게시물에서 실패한 본문·장소·후속 작업을 함께 재시도합니다")
    fun retry(@PathVariable postId: Long, @RequestBody request: RetryRequest, actor: AdminActor) =
        ApiResponse.success(retryPost(RetryPostParsingCommand(postId, actor, request.reason)))

    data class RetryRequest(val reason: String)
}

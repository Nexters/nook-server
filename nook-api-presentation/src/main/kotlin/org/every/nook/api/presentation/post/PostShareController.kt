package org.every.nook.api.presentation.post

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import org.every.nook.api.application.post.share.IssuePostShareUseCase
import org.every.nook.api.application.post.share.SavePostShareUseCase
import org.every.nook.api.presentation.auth.UserContext
import org.every.nook.api.presentation.response.ApiResponse
import org.every.nook.api.presentation.response.toSeoulOffsetDateTime
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@RestController
@Validated
@RequestMapping("/api/v1")
@Tag(name = "Post Share")
class PostShareController(private val issue: IssuePostShareUseCase, private val save: SavePostShareUseCase) {
    @PostMapping("/posts/{postId}/share-links")
    @Operation(summary = "내 저장 게시물의 현재 공개 콘텐츠와 장소 목록을 고정한 공유 토큰 발급")
    fun issue(
        @Parameter(hidden = true) userContext: UserContext,
        @PathVariable @Positive postId: Long,
    ): ApiResponse<LinkResponse> = issue(userContext.userId, postId).let {
        ApiResponse.success(LinkResponse(it.token, it.sharedAt.toSeoulOffsetDateTime()))
    }

    @PostMapping("/post-shares/{token}/save")
    @Operation(summary = "게시물 공유 스냅샷을 내 아카이브에 저장; 기존 저장 건의 장소와 메모는 유지")
    fun save(
        @Parameter(hidden = true) userContext: UserContext,
        @PathVariable token: String,
        @Valid @RequestBody request: SaveRequest,
    ) = ApiResponse.success(save(userContext.userId, token, request.groupIds))

    data class LinkResponse(val token: String, val sharedAt: OffsetDateTime)

    data class SaveRequest(@field:NotEmpty val groupIds: List<@Positive Long>)
}

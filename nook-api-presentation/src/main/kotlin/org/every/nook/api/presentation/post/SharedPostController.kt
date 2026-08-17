package org.every.nook.api.presentation.post

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import org.every.nook.api.application.post.SaveSharedPostUseCase
import org.every.nook.api.presentation.auth.UserContext
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Shared Post")
@Validated
@RestController
@RequestMapping("/api/v1/shared-posts")
class SharedPostController(private val saveSharedPostUseCase: SaveSharedPostUseCase) {
    @Operation(summary = "공유 게시물을 내 아카이브에 저장")
    @PostMapping("/{shareToken}/{sharedPostId}/save")
    fun save(
        @Parameter(hidden = true) userContext: UserContext,
        @Parameter(description = "공유 링크 토큰") @PathVariable shareToken: String,
        @Parameter(description = "공유자의 저장 게시물 식별자") @PathVariable @Positive sharedPostId: Long,
        @Valid @RequestBody request: SaveSharedPostRequest,
    ): ApiResponse<SaveSharedPostResponse> {
        val result = saveSharedPostUseCase(
            SaveSharedPostUseCase.Command(
                userId = userContext.userId,
                shareToken = shareToken,
                sharedPostId = sharedPostId,
                groupIds = request.groupIds,
            ),
        )
        return ApiResponse.success(SaveSharedPostResponse(result.postId))
    }
}

data class SaveSharedPostRequest(
    @field:NotEmpty
    val groupIds: List<@Positive Long>,
)

data class SaveSharedPostResponse(val postId: Long)

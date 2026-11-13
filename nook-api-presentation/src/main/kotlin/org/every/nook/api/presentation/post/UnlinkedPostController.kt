package org.every.nook.api.presentation.post

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.every.nook.api.application.post.ListUnlinkedSavedPostsUseCase
import org.every.nook.api.presentation.auth.UserContext
import org.every.nook.api.presentation.post.response.SavedPostPageResponse
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Post")
@Validated
@RestController
@RequestMapping("/api/v1/posts/unlinked-places")
class UnlinkedPostController(private val useCase: ListUnlinkedSavedPostsUseCase) {
    @Operation(summary = "내 아카이브의 장소 미연결 게시물 목록 조회")
    @GetMapping
    fun list(
        @Parameter(hidden = true) userContext: UserContext,
        @Parameter(description = "페이지 번호. 0부터 시작")
        @RequestParam(defaultValue = "0")
        @Min(0)
        page: Int,
        @Parameter(description = "페이지당 게시물 수. 최대 100")
        @RequestParam(defaultValue = "20")
        @Min(1)
        @Max(MAX_PAGE_SIZE)
        size: Int,
    ): ApiResponse<SavedPostPageResponse> = ApiResponse.success(
        SavedPostPageResponse.from(useCase(ListUnlinkedSavedPostsUseCase.Query(userContext.userId, page, size))),
    )

    private companion object {
        const val MAX_PAGE_SIZE = 100L
    }
}

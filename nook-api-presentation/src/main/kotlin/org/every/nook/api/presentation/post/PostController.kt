package org.every.nook.api.presentation.post

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Positive
import org.every.nook.api.application.group.ReplaceSavedPostGroupsUseCase
import org.every.nook.api.application.post.CreatePostUseCase
import org.every.nook.api.application.post.FindPostPlaceParsingUseCase
import org.every.nook.api.application.post.GetSavedPostDetailUseCase
import org.every.nook.api.application.post.ListSavedPostsUseCase
import org.every.nook.api.application.post.UpdatePostMemoUseCase
import org.every.nook.api.presentation.auth.UserContext
import org.every.nook.api.presentation.post.request.CreatePostRequest
import org.every.nook.api.presentation.post.request.ReplaceSavedPostGroupsRequest
import org.every.nook.api.presentation.post.request.UpdatePostMemoRequest
import org.every.nook.api.presentation.post.response.PostPlaceParsingResponse
import org.every.nook.api.presentation.post.response.PostResponse
import org.every.nook.api.presentation.post.response.SavedPostDetailResponse
import org.every.nook.api.presentation.post.response.SavedPostPageResponse
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val MAX_PAGE_SIZE = 100L

@Tag(name = "Post")
@Validated
@RestController
@RequestMapping("/api/v1/posts")
class PostController(
    private val createPostUseCase: CreatePostUseCase,
    private val findPostPlaceParsingUseCase: FindPostPlaceParsingUseCase,
    private val listSavedPostsUseCase: ListSavedPostsUseCase,
    private val getSavedPostDetailUseCase: GetSavedPostDetailUseCase,
    private val updatePostMemoUseCase: UpdatePostMemoUseCase,
    private val replaceSavedPostGroupsUseCase: ReplaceSavedPostGroupsUseCase,
) {
    @Operation(summary = "URL로 게시물 저장 시작")
    @PostMapping
    fun createPost(
        @Parameter(hidden = true) userContext: UserContext,
        @Valid @RequestBody request: CreatePostRequest,
    ): ResponseEntity<ApiResponse<PostResponse>> {
        val result = createPostUseCase(
            CreatePostUseCase.Command(
                userId = userContext.userId,
                url = request.url,
                memo = request.memo,
                groupIds = request.groupIds,
            ),
        )

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(PostResponse.from(result)))
    }

    @Operation(summary = "저장 게시물 목록 조회")
    @GetMapping
    fun listSavedPosts(
        @Parameter(hidden = true) userContext: UserContext,
        @Parameter(description = "조회할 페이지 번호. 0부터 시작합니다.")
        @RequestParam(defaultValue = "0")
        @Min(0)
        page: Int,
        @Parameter(description = "페이지당 게시물 수")
        @RequestParam(defaultValue = "20")
        @Min(1)
        @Max(MAX_PAGE_SIZE)
        size: Int,
    ): ApiResponse<SavedPostPageResponse> {
        val result = listSavedPostsUseCase(
            ListSavedPostsUseCase.Query(
                userId = userContext.userId,
                page = page,
                size = size,
            ),
        )
        return ApiResponse.success(SavedPostPageResponse.from(result))
    }

    @Operation(summary = "저장 게시물 상세 조회")
    @GetMapping("/{postId}")
    fun getSavedPostDetail(
        @Parameter(hidden = true) userContext: UserContext,
        @Parameter(description = "조회할 저장 게시물 식별자")
        @PathVariable
        @Positive
        postId: Long,
    ): ApiResponse<SavedPostDetailResponse> {
        val result = getSavedPostDetailUseCase(
            GetSavedPostDetailUseCase.Query(
                userId = userContext.userId,
                postId = postId,
            ),
        )
        return ApiResponse.success(SavedPostDetailResponse.from(result))
    }

    @Operation(summary = "내 저장 게시물 메모 변경")
    @PatchMapping("/{postId}/memo")
    fun updateMemo(
        @Parameter(hidden = true) userContext: UserContext,
        @Parameter(description = "메모를 변경할 저장 게시물 식별자")
        @PathVariable
        @Positive
        postId: Long,
        @Valid @RequestBody request: UpdatePostMemoRequest,
    ): ApiResponse<Unit> {
        updatePostMemoUseCase(
            UpdatePostMemoUseCase.Command(
                userId = userContext.userId,
                postId = postId,
                memo = request.memo,
            ),
        )
        return ApiResponse.success(Unit)
    }

    @Operation(summary = "내 저장 게시물 그룹 재지정")
    @PutMapping("/{postId}/groups")
    fun replaceGroups(
        @Parameter(hidden = true) userContext: UserContext,
        @Parameter(description = "그룹을 재지정할 저장 게시물 식별자")
        @PathVariable
        @Positive
        postId: Long,
        @Valid @RequestBody request: ReplaceSavedPostGroupsRequest,
    ): ApiResponse<Unit> {
        replaceSavedPostGroupsUseCase(
            ReplaceSavedPostGroupsUseCase.Command(
                userId = userContext.userId,
                savedPostId = postId,
                groupIds = request.groupIds,
            ),
        )
        return ApiResponse.success(Unit)
    }

    @Operation(summary = "게시물 장소 파싱 결과 조회")
    @GetMapping("/{postId}/place-parsing")
    fun findPlaceParsing(
        @Parameter(hidden = true) userContext: UserContext,
        @Parameter(description = "장소 파싱 결과를 조회할 게시물 식별자")
        @PathVariable
        @Positive
        postId: Long,
    ): ApiResponse<PostPlaceParsingResponse> {
        val result = findPostPlaceParsingUseCase(
            FindPostPlaceParsingUseCase.Query(
                userId = userContext.userId,
                postId = postId,
            ),
        )

        return ApiResponse.success(PostPlaceParsingResponse.from(result))
    }
}

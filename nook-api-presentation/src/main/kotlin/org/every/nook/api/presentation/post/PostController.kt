package org.every.nook.api.presentation.post

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.every.nook.api.application.post.CreatePostUseCase
import org.every.nook.api.application.post.FindPostPlaceParsingUseCase
import org.every.nook.api.application.post.UpdatePostPlaceBookmarkUseCase
import org.every.nook.api.presentation.auth.UserContext
import org.every.nook.api.presentation.post.request.CreatePostRequest
import org.every.nook.api.presentation.post.request.UpdatePlaceBookmarkRequest
import org.every.nook.api.presentation.post.response.PostPlaceParsingResponse
import org.every.nook.api.presentation.post.response.PostResponse
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Post")
@Validated
@RestController
@RequestMapping("/api/v1/posts")
class PostController(
    private val createPostUseCase: CreatePostUseCase,
    private val findPostPlaceParsingUseCase: FindPostPlaceParsingUseCase,
    private val updatePostPlaceBookmarkUseCase: UpdatePostPlaceBookmarkUseCase,
) {
    @Operation(summary = "URL로 게시물 생성")
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
            ),
        )

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(PostResponse.from(result)))
    }

    @Operation(summary = "게시물 연관 장소 북마크 변경")
    @PatchMapping("/{postId}/places/{placeId}/bookmark")
    fun updatePlaceBookmark(
        @Parameter(hidden = true) userContext: UserContext,
        @PathVariable @Positive postId: Long,
        @PathVariable @Positive placeId: Long,
        @Valid @RequestBody request: UpdatePlaceBookmarkRequest,
    ): ApiResponse<Unit> {
        updatePostPlaceBookmarkUseCase(
            UpdatePostPlaceBookmarkUseCase.Command(
                userId = userContext.userId,
                postId = postId,
                placeId = placeId,
                bookmarked = request.bookmarked,
            ),
        )
        return ApiResponse.success(Unit)
    }

    @Operation(summary = "게시물 장소 파싱 결과 조회")
    @GetMapping("/{postId}/place-parsing")
    fun findPlaceParsing(
        @Parameter(hidden = true) userContext: UserContext,
        @PathVariable @Positive postId: Long,
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

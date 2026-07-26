package org.every.nook.api.presentation.save

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.every.nook.api.application.save.FindSavedPostPlaceParsingUseCase
import org.every.nook.api.application.save.SaveInstagramPostUseCase
import org.every.nook.api.application.save.UpdateSavedPostPlaceBookmarkUseCase
import org.every.nook.api.presentation.response.ApiResponse
import org.every.nook.api.presentation.save.request.SaveInstagramPostRequest
import org.every.nook.api.presentation.save.request.UpdatePlaceBookmarkRequest
import org.every.nook.api.presentation.save.response.SavedPostPlaceParsingResponse
import org.every.nook.api.presentation.save.response.SavedPostResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Saved Post")
@Validated
@RestController
@RequestMapping("/api/v1/saved-posts")
class SavedPostController(
    private val saveInstagramPostUseCase: SaveInstagramPostUseCase,
    private val findSavedPostPlaceParsingUseCase: FindSavedPostPlaceParsingUseCase,
    private val updateSavedPostPlaceBookmarkUseCase: UpdateSavedPostPlaceBookmarkUseCase,
) {
    @Operation(summary = "Instagram 게시물 URL 저장")
    @PostMapping
    fun saveInstagramPost(
        @RequestHeader(USER_ID_HEADER) @Positive userId: Long,
        @Valid @RequestBody request: SaveInstagramPostRequest,
    ): ResponseEntity<ApiResponse<SavedPostResponse>> {
        val result = saveInstagramPostUseCase(
            SaveInstagramPostUseCase.Command(
                userId = userId,
                instagramUrl = request.instagramUrl,
                memo = request.memo,
            ),
        )

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(SavedPostResponse.from(result)))
    }

    @Operation(summary = "저장 게시물 연관 장소 북마크 변경")
    @PatchMapping("/{savedPostId}/places/{placeId}/bookmark")
    fun updatePlaceBookmark(
        @RequestHeader(USER_ID_HEADER) @Positive userId: Long,
        @PathVariable @Positive savedPostId: Long,
        @PathVariable @Positive placeId: Long,
        @Valid @RequestBody request: UpdatePlaceBookmarkRequest,
    ): ApiResponse<Unit> {
        updateSavedPostPlaceBookmarkUseCase(
            UpdateSavedPostPlaceBookmarkUseCase.Command(
                userId = userId,
                savedPostId = savedPostId,
                placeId = placeId,
                bookmarked = request.bookmarked,
            ),
        )
        return ApiResponse.success(Unit)
    }

    @Operation(summary = "저장 게시물 장소 파싱 결과 조회")
    @GetMapping("/{savedPostId}/place-parsing")
    fun findPlaceParsing(
        @RequestHeader(USER_ID_HEADER) @Positive userId: Long,
        @PathVariable @Positive savedPostId: Long,
    ): ApiResponse<SavedPostPlaceParsingResponse> {
        val result = findSavedPostPlaceParsingUseCase(
            FindSavedPostPlaceParsingUseCase.Query(
                userId = userId,
                savedPostId = savedPostId,
            ),
        )

        return ApiResponse.success(SavedPostPlaceParsingResponse.from(result))
    }

    companion object {
        const val USER_ID_HEADER = "X-Nook-User-Id"
    }
}

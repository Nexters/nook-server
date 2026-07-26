package org.every.nook.api.presentation.save

import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.every.nook.api.application.save.FindSavedPostPlaceParsingUseCase
import org.every.nook.api.application.save.SaveInstagramPostUseCase
import org.every.nook.api.presentation.response.ApiResponse
import org.every.nook.api.presentation.save.request.SaveInstagramPostRequest
import org.every.nook.api.presentation.save.response.SavedPostPlaceParsingResponse
import org.every.nook.api.presentation.save.response.SavedPostResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/v1/saved-posts")
class SavedPostController(
    private val saveInstagramPostUseCase: SaveInstagramPostUseCase,
    private val findSavedPostPlaceParsingUseCase: FindSavedPostPlaceParsingUseCase,
) {
    @PostMapping
    fun saveInstagramPost(
        @RequestHeader(USER_ID_HEADER) @Positive userId: Long,
        @Valid @RequestBody request: SaveInstagramPostRequest,
    ): ResponseEntity<ApiResponse<SavedPostResponse>> {
        val result = saveInstagramPostUseCase(
            SaveInstagramPostUseCase.Command(
                userId = userId,
                instagramUrl = request.instagramUrl,
            ),
        )

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(SavedPostResponse.from(result)))
    }

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

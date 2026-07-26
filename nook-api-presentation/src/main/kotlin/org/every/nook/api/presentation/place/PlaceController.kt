package org.every.nook.api.presentation.place

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.every.nook.api.application.place.UpdatePlaceBookmarkUseCase
import org.every.nook.api.presentation.auth.UserContext
import org.every.nook.api.presentation.place.request.UpdatePlaceBookmarkRequest
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Place")
@Validated
@RestController
@RequestMapping("/api/v1/places")
class PlaceController(private val updatePlaceBookmarkUseCase: UpdatePlaceBookmarkUseCase) {
    @Operation(summary = "장소 북마크 변경")
    @PatchMapping("/{placeId}/bookmark")
    fun updateBookmark(
        @Parameter(hidden = true) userContext: UserContext,
        @PathVariable @Positive placeId: Long,
        @Valid @RequestBody request: UpdatePlaceBookmarkRequest,
    ): ApiResponse<Unit> {
        updatePlaceBookmarkUseCase(
            UpdatePlaceBookmarkUseCase.Command(
                userId = userContext.userId,
                placeId = placeId,
                bookmarked = request.bookmarked,
            ),
        )
        return ApiResponse.success(Unit)
    }
}

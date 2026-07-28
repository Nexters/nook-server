package org.every.nook.api.presentation.place.request

import io.swagger.v3.oas.annotations.media.Schema

data class UpdatePlaceBookmarkRequest(
    @field:Schema(description = "북마크 여부")
    val bookmarked: Boolean,
)

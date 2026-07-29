package org.every.nook.api.presentation.post.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ConnectPostPlaceRequest(
    @field:Schema(description = "장소 검색 응답에서 받은 만료형 선택 토큰")
    @field:NotBlank
    @field:Size(max = MAX_SELECTION_TOKEN_LENGTH)
    val selectionToken: String,
) {
    private companion object {
        const val MAX_SELECTION_TOKEN_LENGTH = 4096
    }
}

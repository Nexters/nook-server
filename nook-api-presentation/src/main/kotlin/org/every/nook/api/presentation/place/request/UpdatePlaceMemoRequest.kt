package org.every.nook.api.presentation.place.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size

data class UpdatePlaceMemoRequest(
    @field:Schema(
        description = "장소 메모. null이면 기존 메모를 삭제합니다.",
        nullable = true,
        maxLength = MAX_MEMO_LENGTH,
    )
    @field:Size(max = MAX_MEMO_LENGTH)
    val memo: String?,
) {
    companion object {
        const val MAX_MEMO_LENGTH = 2000
    }
}

package org.every.nook.api.presentation.post.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size

data class UpdatePostMemoRequest(
    @field:Schema(
        description = "사용자 메모. null이면 기존 메모를 삭제합니다.",
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

package org.every.nook.api.presentation.error

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "요청 필드 검증 오류")
data class FieldViolation(
    @field:Schema(description = "오류가 발생한 필드 경로")
    val field: String,
    @field:Schema(description = "검증 실패 사유")
    val reason: String,
)

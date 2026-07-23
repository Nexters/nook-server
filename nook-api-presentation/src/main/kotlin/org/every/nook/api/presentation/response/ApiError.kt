package org.every.nook.api.presentation.response

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "API 오류")
data class ApiError(
    @field:Schema(description = "안정적인 오류 코드", example = "GROUP_NAME_DUPLICATED")
    val errorCode: String,
    @field:Schema(description = "클라이언트에 공개 가능한 오류 사유")
    val reason: String,
    @field:JsonInclude(JsonInclude.Include.ALWAYS)
    @field:Schema(description = "오류별 부가 데이터", nullable = true)
    val data: Map<String, Any?>? = null,
)

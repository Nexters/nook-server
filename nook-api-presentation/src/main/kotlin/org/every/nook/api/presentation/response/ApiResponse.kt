package org.every.nook.api.presentation.response

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "공통 API 응답")
@JsonInclude(JsonInclude.Include.NON_NULL)
class ApiResponse<out T : Any> private constructor(
    @field:Schema(description = "응답 결과 유형")
    val resultType: ResultType,
    @field:Schema(description = "성공 응답 본문", nullable = true)
    val success: T? = null,
    @field:Schema(description = "실패 응답", nullable = true)
    val error: ApiError? = null,
) {
    companion object {
        fun <T : Any> success(body: T): ApiResponse<T> = ApiResponse(
            resultType = ResultType.SUCCESS,
            success = body,
        )

        fun fail(error: ApiError): ApiResponse<Nothing> = ApiResponse(
            resultType = ResultType.FAIL,
            error = error,
        )
    }
}

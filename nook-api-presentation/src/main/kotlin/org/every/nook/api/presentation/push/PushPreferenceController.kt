package org.every.nook.api.presentation.push

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.every.nook.api.application.push.GetPushPreferenceUseCase
import org.every.nook.api.application.push.PushPreference
import org.every.nook.api.application.push.UpdatePushPreferenceUseCase
import org.every.nook.api.presentation.auth.UserContext
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Push")
@RestController
@RequestMapping("/api/v1/me/push-preferences")
class PushPreferenceController(
    private val getPushPreferenceUseCase: GetPushPreferenceUseCase,
    private val updatePushPreferenceUseCase: UpdatePushPreferenceUseCase,
) {
    @Operation(summary = "내 푸시 알림 설정 조회")
    @GetMapping
    fun get(@Parameter(hidden = true) userContext: UserContext): ApiResponse<PushPreferenceResponse> =
        ApiResponse.success(getPushPreferenceUseCase(GetPushPreferenceUseCase.Query(userContext.userId)).toResponse())

    @Operation(summary = "내 푸시 알림 설정 변경")
    @PatchMapping
    fun update(
        @Parameter(hidden = true) userContext: UserContext,
        @Valid @RequestBody request: UpdatePushPreferenceRequest,
    ): ApiResponse<PushPreferenceResponse> = ApiResponse.success(
        updatePushPreferenceUseCase(
            UpdatePushPreferenceUseCase.Command(userContext.userId, request.postProcessingEnabled),
        ).toResponse(),
    )

    private fun PushPreference.toResponse() = PushPreferenceResponse(postProcessingEnabled)
}

data class UpdatePushPreferenceRequest(
    @field:Schema(description = "게시물 저장 처리 결과 푸시 알림 수신 여부")
    val postProcessingEnabled: Boolean,
)

data class PushPreferenceResponse(
    @field:Schema(description = "게시물 저장 처리 결과 푸시 알림 수신 여부")
    val postProcessingEnabled: Boolean,
)

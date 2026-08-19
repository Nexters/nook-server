package org.every.nook.api.presentation.push

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.every.nook.api.application.push.DeletePushTokenUseCase
import org.every.nook.api.application.push.PushPlatform
import org.every.nook.api.application.push.RegisterPushTokenUseCase
import org.every.nook.api.logging.PrivacyArgument
import org.every.nook.api.presentation.auth.UserContext
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Push")
@Validated
@RestController
@RequestMapping("/api/v1/me/push-tokens")
class PushTokenController(
    private val registerPushTokenUseCase: RegisterPushTokenUseCase,
    private val deletePushTokenUseCase: DeletePushTokenUseCase,
) {
    @Operation(summary = "내 기기 푸시 토큰 등록")
    @PutMapping
    fun register(
        @Parameter(hidden = true) userContext: UserContext,
        @Valid @RequestBody request: RegisterPushTokenRequest,
    ): ApiResponse<Unit> {
        registerPushTokenUseCase(
            RegisterPushTokenUseCase.Command(
                userId = userContext.userId,
                token = request.token,
                platform = request.platform,
            ),
        )
        return ApiResponse.success(Unit)
    }

    @Operation(summary = "내 기기 푸시 토큰 삭제")
    @DeleteMapping
    fun delete(
        @Parameter(hidden = true) userContext: UserContext,
        @Valid @RequestBody request: DeletePushTokenRequest,
    ): ApiResponse<Unit> {
        deletePushTokenUseCase(DeletePushTokenUseCase.Command(userContext.userId, request.token))
        return ApiResponse.success(Unit)
    }
}

data class RegisterPushTokenRequest(
    @field:PrivacyArgument
    @field:NotBlank
    @field:Size(max = 512)
    val token: String,
    val platform: PushPlatform,
)

data class DeletePushTokenRequest(
    @field:PrivacyArgument
    @field:NotBlank
    @field:Size(max = 512)
    val token: String,
)

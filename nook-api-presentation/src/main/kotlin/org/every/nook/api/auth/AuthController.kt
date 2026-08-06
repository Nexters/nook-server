package org.every.nook.api.auth

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.every.nook.api.application.auth.AuthenticateSocialUserUseCase
import org.every.nook.api.application.auth.LoginTokens
import org.every.nook.api.application.auth.RefreshLoginTokenUseCase
import org.every.nook.api.application.auth.SocialCredential
import org.every.nook.api.application.auth.SocialLoginProvider
import org.every.nook.api.application.member.LogoutMemberUseCase
import org.every.nook.api.presentation.auth.UserContext
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "인증 API")
class AuthController(
    private val authenticateSocialUserUseCase: AuthenticateSocialUserUseCase,
    private val refreshLoginTokenUseCase: RefreshLoginTokenUseCase,
    private val logoutMemberUseCase: LogoutMemberUseCase,
) {
    @Operation(summary = "소셜 로그인", security = [])
    @PostMapping("/social")
    fun authenticateSocial(
        @Valid @RequestBody request: SocialAuthRequest,
    ): ResponseEntity<ApiResponse<SocialAuthResponse>> {
        val result = authenticateSocialUserUseCase(request.toCredential())
        return ResponseEntity.ok(ApiResponse.success(SocialAuthResponse.from(result.tokens)))
    }

    @Operation(summary = "로그인 토큰 재발급", security = [])
    @PostMapping("/token/refresh")
    fun refresh(@Valid @RequestBody request: RefreshTokenRequest): ResponseEntity<ApiResponse<TokenResponse>> =
        ResponseEntity.ok(
            ApiResponse.success(TokenResponse.from(refreshLoginTokenUseCase(request.refreshToken))),
        )

    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    fun logout(@Parameter(hidden = true) userContext: UserContext): ResponseEntity<ApiResponse<AuthActionResponse>> {
        logoutMemberUseCase(userContext.userId)
        return ResponseEntity.ok(ApiResponse.success(AuthActionResponse()))
    }
}

data class SocialAuthRequest(
    @field:Schema(description = "소셜 로그인 provider", nullable = true)
    @field:NotNull
    val provider: SocialLoginProvider?,
    @field:Schema(description = "provider access token", nullable = true)
    val accessToken: String? = null,
    @field:Schema(description = "provider identity token", nullable = true)
    val identityToken: String? = null,
    @field:Schema(description = "provider authorization code", nullable = true)
    val authorizationCode: String? = null,
) {
    fun toCredential(): SocialCredential = SocialCredential(
        provider = requireNotNull(provider),
        accessToken = accessToken,
        identityToken = identityToken,
        authorizationCode = authorizationCode,
    )
}

data class RefreshTokenRequest(
    @field:Schema(description = "서비스 refresh token")
    @field:NotBlank
    val refreshToken: String,
)

data class SocialAuthResponse(
    @field:Schema(description = "서비스 access token")
    val accessToken: String,
    @field:Schema(description = "서비스 refresh token")
    val refreshToken: String,
) {
    companion object {
        fun from(tokens: LoginTokens): SocialAuthResponse = SocialAuthResponse(tokens.accessToken, tokens.refreshToken)
    }
}

data class TokenResponse(
    @field:Schema(description = "서비스 access token")
    val accessToken: String,
    @field:Schema(description = "서비스 refresh token")
    val refreshToken: String,
) {
    companion object {
        fun from(tokens: LoginTokens): TokenResponse = TokenResponse(tokens.accessToken, tokens.refreshToken)
    }
}

data class AuthActionResponse(
    @field:Schema(description = "처리 완료 여부")
    val completed: Boolean = true,
)

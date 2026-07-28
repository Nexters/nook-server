package org.every.nook.api.auth

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.every.nook.api.application.auth.AuthenticateSocialUserUseCase
import org.every.nook.api.application.auth.LoginTokens
import org.every.nook.api.application.auth.RefreshLoginTokenUseCase
import org.every.nook.api.application.auth.SocialAuthenticationResult
import org.every.nook.api.application.auth.SocialCredential
import org.every.nook.api.application.auth.SocialLoginProvider
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
) {
    @Operation(summary = "소셜 로그인", security = [])
    @PostMapping("/social")
    fun authenticateSocial(
        @Valid @RequestBody request: SocialAuthRequest,
    ): ResponseEntity<ApiResponse<SocialAuthResponse>> {
        val result = authenticateSocialUserUseCase(request.toCredential())
        return ResponseEntity.ok(ApiResponse.success(SocialAuthResponse.from(result)))
    }

    @Operation(summary = "로그인 토큰 재발급", security = [])
    @PostMapping("/token/refresh")
    fun refresh(@Valid @RequestBody request: RefreshTokenRequest): ResponseEntity<ApiResponse<TokenResponse>> =
        ResponseEntity.ok(
            ApiResponse.success(TokenResponse.from(refreshLoginTokenUseCase(request.refreshToken))),
        )
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

enum class SocialAuthStatus {
    SIGNED_IN,
    SIGNUP_REQUIRED,
}

data class SocialAuthResponse(
    @field:Schema(description = "소셜 인증 결과 상태")
    val status: SocialAuthStatus,
    @field:Schema(description = "서비스 access token. 로그인 완료 상태에서만 내려갑니다.", nullable = true)
    val accessToken: String? = null,
    @field:Schema(description = "서비스 refresh token. 로그인 완료 상태에서만 내려갑니다.", nullable = true)
    val refreshToken: String? = null,
    @field:Schema(description = "회원가입에 사용할 임시 token. 회원가입 필요 상태에서만 내려갑니다.", nullable = true)
    val signupToken: String? = null,
) {
    companion object {
        fun from(result: SocialAuthenticationResult): SocialAuthResponse = when (result) {
            is SocialAuthenticationResult.SignedIn -> SocialAuthResponse(
                status = SocialAuthStatus.SIGNED_IN,
                accessToken = result.tokens.accessToken,
                refreshToken = result.tokens.refreshToken,
            )

            is SocialAuthenticationResult.SignupRequired -> SocialAuthResponse(
                status = SocialAuthStatus.SIGNUP_REQUIRED,
                signupToken = result.signupToken,
            )
        }
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

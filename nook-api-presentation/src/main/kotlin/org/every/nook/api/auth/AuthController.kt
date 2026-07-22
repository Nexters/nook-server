package org.every.nook.api.auth

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.every.nook.api.application.auth.AuthenticateSocialUserUseCase
import org.every.nook.api.application.auth.LoginTokens
import org.every.nook.api.application.auth.RefreshLoginTokenUseCase
import org.every.nook.api.application.auth.SocialAuthenticationResult
import org.every.nook.api.application.auth.SocialCredential
import org.every.nook.api.application.auth.SocialLoginProvider
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authenticateSocialUserUseCase: AuthenticateSocialUserUseCase,
    private val refreshLoginTokenUseCase: RefreshLoginTokenUseCase,
) {
    @PostMapping("/social")
    fun authenticateSocial(@Valid @RequestBody request: SocialAuthRequest): ResponseEntity<SocialAuthResponse> {
        val result = authenticateSocialUserUseCase(request.toCredential())
        return ResponseEntity.ok(SocialAuthResponse.from(result))
    }

    @PostMapping("/token/refresh")
    fun refresh(@Valid @RequestBody request: RefreshTokenRequest): ResponseEntity<TokenResponse> =
        ResponseEntity.ok(TokenResponse.from(refreshLoginTokenUseCase(request.refreshToken)))
}

data class SocialAuthRequest(
    @field:NotNull
    val provider: SocialLoginProvider?,
    val accessToken: String? = null,
    val identityToken: String? = null,
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
    @field:NotBlank
    val refreshToken: String,
)

enum class SocialAuthStatus {
    SIGNED_IN,
    SIGNUP_REQUIRED,
}

data class SocialAuthResponse(
    val status: SocialAuthStatus,
    val accessToken: String? = null,
    val refreshToken: String? = null,
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

data class TokenResponse(val accessToken: String, val refreshToken: String) {
    companion object {
        fun from(tokens: LoginTokens): TokenResponse = TokenResponse(tokens.accessToken, tokens.refreshToken)
    }
}

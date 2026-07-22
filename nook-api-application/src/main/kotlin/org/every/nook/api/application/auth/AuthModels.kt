package org.every.nook.api.application.auth

import org.every.nook.api.domain.member.SocialProvider
import java.time.Instant

data class SocialIdentity(val provider: SocialProvider, val subject: String)

data class SignupClaims(val provider: SocialProvider, val subject: String)

data class RefreshClaims(val memberId: Long, val tokenIdentifier: String)

data class IssuedToken(val value: String, val identifier: String, val expiresAt: Instant)

data class LoginTokens(val accessToken: String, val refreshToken: String)

sealed interface SocialAuthenticationResult {
    data class SignedIn(val tokens: LoginTokens) : SocialAuthenticationResult

    data class SignupRequired(val signupToken: String) : SocialAuthenticationResult
}

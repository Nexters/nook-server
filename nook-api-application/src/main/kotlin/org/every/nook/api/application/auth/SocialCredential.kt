package org.every.nook.api.application.auth

enum class SocialLoginProvider {
    KAKAO,
    APPLE,
}

data class SocialCredential(
    val provider: SocialLoginProvider,
    val accessToken: String? = null,
    val identityToken: String? = null,
    val authorizationCode: String? = null,
)

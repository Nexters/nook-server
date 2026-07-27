package org.every.nook.api.infrastructure.auth

import org.every.nook.api.application.auth.InvalidSocialCredentialException
import org.every.nook.api.application.auth.SocialCredential
import org.every.nook.api.application.auth.SocialIdentity
import org.every.nook.api.domain.member.SocialProvider
import org.springframework.security.oauth2.jwt.JwtDecoder

class GoogleSocialIdentityProvider(private val jwtDecoder: JwtDecoder) {
    fun authenticate(credential: SocialCredential): SocialIdentity {
        val identityToken = credential.identityToken?.takeIf(String::isNotBlank)
            ?: throw InvalidSocialCredentialException()
        val jwt = runCatching { jwtDecoder.decode(identityToken) }
            .getOrElse { throw InvalidSocialCredentialException() }
        return SocialIdentity(SocialProvider.GOOGLE, jwt.subject)
    }
}

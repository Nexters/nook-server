package org.every.nook.api.infrastructure.auth

import org.every.nook.api.application.auth.SocialCredential
import org.every.nook.api.application.auth.SocialIdentity
import org.every.nook.api.application.auth.SocialLoginProvider
import org.every.nook.api.application.auth.port.SocialIdentityProvider

class CompositeSocialIdentityProvider(
    private val kakaoProvider: KakaoSocialIdentityProvider,
    private val appleProvider: AppleSocialIdentityProvider,
) : SocialIdentityProvider {
    override fun authenticate(credential: SocialCredential): SocialIdentity = when (credential.provider) {
        SocialLoginProvider.KAKAO -> kakaoProvider.authenticate(credential)
        SocialLoginProvider.APPLE -> appleProvider.authenticate(credential)
    }
}

package org.every.nook.api.infrastructure.auth

import org.every.nook.api.application.auth.InvalidSocialCredentialException
import org.every.nook.api.application.auth.SocialCredential
import org.every.nook.api.application.auth.SocialLoginProvider
import org.every.nook.api.domain.member.SocialProvider
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GoogleSocialIdentityProviderTest {
    @Test
    fun `verified Google ID token subject is used as social identity`() {
        val decoder = JwtDecoder { token ->
            Jwt(
                token,
                Instant.parse("2026-07-22T00:00:00Z"),
                Instant.parse("2026-07-22T01:00:00Z"),
                mapOf("alg" to "RS256"),
                mapOf("sub" to "google-subject"),
            )
        }
        val provider = GoogleSocialIdentityProvider(decoder)

        val identity = provider.authenticate(
            SocialCredential(provider = SocialLoginProvider.GOOGLE, identityToken = "id-token"),
        )

        assertEquals(SocialProvider.GOOGLE, identity.provider)
        assertEquals("google-subject", identity.subject)
    }

    @Test
    fun `missing Google ID token is rejected`() {
        val provider = GoogleSocialIdentityProvider(JwtDecoder { error("must not decode") })

        assertFailsWith<InvalidSocialCredentialException> {
            provider.authenticate(SocialCredential(provider = SocialLoginProvider.GOOGLE))
        }
    }
}

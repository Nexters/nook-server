package org.every.nook.api.infrastructure.auth

import org.every.nook.api.application.auth.InvalidRefreshTokenException
import org.every.nook.api.domain.member.SocialProvider
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val ACCESS_SECRET = "access-secret-must-have-at-least-32-bytes"
private const val REFRESH_SECRET = "refresh-secret-must-have-at-least-32-bytes"

class JwtTokenProviderTest {
    private val instant = Instant.parse("2026-07-22T00:00:00Z")
    private val properties = JwtProperties(
        accessSecret = ACCESS_SECRET,
        refreshSecret = REFRESH_SECRET,
        accessTtl = Duration.ofMinutes(30),
        refreshTtl = Duration.ofDays(30),
        signupTtl = Duration.ofMinutes(10),
    )

    @Test
    fun `signup token preserves provider and subject`() {
        val provider = JwtTokenProvider(properties, Clock.fixed(instant, ZoneOffset.UTC))

        val token = provider.issueSignupToken(SocialProvider.KAKAO, "kakao-subject")

        assertEquals(
            SocialProvider.KAKAO,
            provider.parseSignupToken(token).provider,
        )
        assertEquals("kakao-subject", provider.parseSignupToken(token).subject)
    }

    @Test
    fun `refresh token cannot be parsed after expiration`() {
        val issuer = JwtTokenProvider(properties, Clock.fixed(instant, ZoneOffset.UTC))
        val token = issuer.issueRefreshToken(1).value
        val expiredClock = Clock.fixed(instant.plus(Duration.ofDays(31)), ZoneOffset.UTC)
        val verifier = JwtTokenProvider(properties, expiredClock)

        assertFailsWith<InvalidRefreshTokenException> {
            verifier.parseRefreshToken(token)
        }
    }

    @Test
    fun `refresh token hash is deterministic without storing original token`() {
        val provider = JwtTokenProvider(properties, Clock.fixed(instant, ZoneOffset.UTC))

        assertEquals(provider.hash("refresh-token"), provider.hash("refresh-token"))
        assertEquals(64, provider.hash("refresh-token").length)
    }
}

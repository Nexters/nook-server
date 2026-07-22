package org.every.nook.api.application.auth

import org.every.nook.api.application.auth.port.RefreshTokenRepository
import org.every.nook.api.application.auth.port.StoredRefreshToken
import org.every.nook.api.application.auth.port.TokenProvider
import org.every.nook.api.application.member.port.MemberRepository
import org.every.nook.api.application.port.TransactionRunner
import org.every.nook.api.domain.member.Member
import org.every.nook.api.domain.member.SocialAccount
import org.every.nook.api.domain.member.SocialProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RefreshLoginTokenUseCaseTest {
    private val now = Instant.parse("2026-07-23T00:00:00Z")
    private val tokenProvider = RefreshFakeTokenProvider(now)
    private val memberRepository = ExistingMemberRepository()

    @Test
    fun `valid refresh token is rotated`() {
        val repository = InMemoryRefreshTokenRepository(activeToken(now))
        val useCase = useCase(repository)

        val result = useCase("old-token")

        assertEquals("new-access", result.accessToken)
        assertEquals("new-refresh", result.refreshToken)
        assertEquals(2, repository.replacedByTokenId)
        assertEquals(now, repository.revokedAt)
    }

    @Test
    fun `reuse of revoked refresh token revokes remaining member tokens`() {
        val repository = InMemoryRefreshTokenRepository(activeToken(now).copy(revokedAt = now.minusSeconds(1)))
        val useCase = useCase(repository)

        assertFailsWith<ReusedRefreshTokenException> {
            useCase("old-token")
        }
        assertTrue(repository.revokedAll)
    }

    private fun useCase(repository: RefreshTokenRepository) = RefreshLoginTokenUseCase(
        tokenProvider = tokenProvider,
        refreshTokenRepository = repository,
        memberRepository = memberRepository,
        transactionRunner = RefreshDirectTransactionRunner,
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )

    private fun activeToken(now: Instant) = StoredRefreshToken(
        id = 1,
        memberId = 1,
        tokenIdentifier = "old-id",
        tokenHash = "hash-old-token",
        expiresAt = now.plusSeconds(3600),
    )
}

private object RefreshDirectTransactionRunner : TransactionRunner {
    override fun <T> required(block: () -> T): T = block()
}

private class RefreshFakeTokenProvider(private val now: Instant) : TokenProvider {
    override fun issueAccessToken(memberId: Long): String = "new-access"

    override fun issueRefreshToken(memberId: Long): IssuedToken = IssuedToken(
        value = "new-refresh",
        identifier = "new-id",
        expiresAt = now.plusSeconds(3600),
    )

    override fun issueSignupToken(provider: SocialProvider, subject: String): String = error("Not used")

    override fun parseRefreshToken(token: String): RefreshClaims = RefreshClaims(1, "old-id")

    override fun parseSignupToken(token: String): SignupClaims = error("Not used")

    override fun hash(token: String): String = "hash-$token"
}

private class InMemoryRefreshTokenRepository(private val existing: StoredRefreshToken) : RefreshTokenRepository {
    var revokedAt: Instant? = null
    var replacedByTokenId: Long? = null
    var revokedAll: Boolean = false

    override fun save(token: StoredRefreshToken): StoredRefreshToken = token.copy(id = 2)

    override fun findByIdentifierForUpdate(identifier: String): StoredRefreshToken = existing

    override fun revoke(tokenId: Long, revokedAt: Instant, replacedByTokenId: Long) {
        this.revokedAt = revokedAt
        this.replacedByTokenId = replacedByTokenId
    }

    override fun revokeActiveTokens(memberId: Long, revokedAt: Instant) {
        revokedAll = true
    }
}

private class ExistingMemberRepository : MemberRepository {
    override fun findMemberId(provider: SocialProvider, subject: String): Long? = 1

    override fun existsByNickname(nickname: String): Boolean = false

    override fun existsSocialAccount(provider: SocialProvider, subject: String): Boolean = true

    override fun save(member: Member): Member = error("Not used")

    override fun saveSocialAccount(account: SocialAccount): SocialAccount = error("Not used")

    override fun existsMember(memberId: Long): Boolean = memberId == 1L
}

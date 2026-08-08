package org.every.nook.api.application.member

import org.every.nook.api.application.auth.IssueLoginTokens
import org.every.nook.api.application.auth.IssuedToken
import org.every.nook.api.application.auth.RefreshClaims
import org.every.nook.api.application.auth.SignupClaims
import org.every.nook.api.application.auth.port.RefreshTokenRepository
import org.every.nook.api.application.auth.port.StoredRefreshToken
import org.every.nook.api.application.auth.port.TokenProvider
import org.every.nook.api.application.member.port.MemberProfile
import org.every.nook.api.application.member.port.MemberRepository
import org.every.nook.api.application.port.TransactionRunner
import org.every.nook.api.domain.member.Member
import org.every.nook.api.domain.member.SocialAccount
import org.every.nook.api.domain.member.SocialProvider
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SignupMemberUseCaseTest {
    private val memberRepository = FakeMemberRepository()
    private val tokenProvider = FakeTokenProvider()
    private val refreshTokenRepository = FakeRefreshTokenRepository()
    private val useCase = SignupMemberUseCase(
        tokenProvider = tokenProvider,
        memberRepository = memberRepository,
        issueLoginTokens = IssueLoginTokens(tokenProvider, refreshTokenRepository),
        transactionRunner = DirectTransactionRunner,
    )

    @Test
    fun `new social member is created with normalized nickname`() {
        val tokens = useCase(
            SignupMemberCommand(
                signupToken = "signup-token",
                nickname = "  누커  ",
                profileImageUrl = null,
            ),
        )

        assertEquals("access-1", tokens.accessToken)
        assertEquals("refresh-1", tokens.refreshToken)
        assertEquals("누커", memberRepository.members.single().nickname)
        assertEquals(SocialProvider.KAKAO, memberRepository.accounts.single().provider)
    }

    @Test
    fun `duplicate nickname is rejected before member creation`() {
        memberRepository.nicknames += "누커"

        assertFailsWith<DuplicateNicknameException> {
            useCase(SignupMemberCommand("signup-token", "누커", null))
        }
        assertEquals(0, memberRepository.members.size)
    }
}

private object DirectTransactionRunner : TransactionRunner {
    override fun <T> required(block: () -> T): T = block()
}

private class FakeMemberRepository : MemberRepository {
    val members = mutableListOf<Member>()
    val accounts = mutableListOf<SocialAccount>()
    val nicknames = mutableSetOf<String>()

    override fun findMemberId(provider: SocialProvider, subject: String): Long? = null

    override fun findMemberProfile(memberId: Long): MemberProfile? = null

    override fun existsByNickname(nickname: String): Boolean = nickname in nicknames

    override fun existsSocialAccount(provider: SocialProvider, subject: String): Boolean = false

    override fun save(member: Member): Member = member.copy(id = (members.size + 1).toLong()).also {
        members += it
        nicknames += it.nickname
    }

    override fun saveSocialAccount(account: SocialAccount): SocialAccount = account.copy(id = 1).also(accounts::add)

    override fun existsMember(memberId: Long): Boolean = members.any { it.id == memberId }
}

private class FakeRefreshTokenRepository : RefreshTokenRepository {
    override fun save(token: StoredRefreshToken): StoredRefreshToken = token.copy(id = 1)

    override fun findByIdentifierForUpdate(identifier: String): StoredRefreshToken? = null

    override fun revoke(tokenId: Long, revokedAt: Instant, replacedByTokenId: Long) = Unit

    override fun revokeActiveTokens(memberId: Long, revokedAt: Instant) = Unit
}

private class FakeTokenProvider : TokenProvider {
    override fun issueAccessToken(memberId: Long): String = "access-$memberId"

    override fun issueRefreshToken(memberId: Long): IssuedToken = IssuedToken(
        value = "refresh-$memberId",
        identifier = "identifier-$memberId",
        expiresAt = Instant.parse("2026-08-22T00:00:00Z"),
    )

    override fun issueSignupToken(provider: SocialProvider, subject: String): String = "signup-token"

    override fun parseRefreshToken(token: String): RefreshClaims = error("Not used")

    override fun parseSignupToken(token: String): SignupClaims = SignupClaims(SocialProvider.KAKAO, "subject")

    override fun hash(token: String): String = "hash-$token"
}

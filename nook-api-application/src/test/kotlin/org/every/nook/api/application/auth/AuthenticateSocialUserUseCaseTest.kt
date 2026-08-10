package org.every.nook.api.application.auth

import org.every.nook.api.application.auth.port.RefreshTokenRepository
import org.every.nook.api.application.auth.port.SocialIdentityProvider
import org.every.nook.api.application.auth.port.StoredRefreshToken
import org.every.nook.api.application.auth.port.TokenProvider
import org.every.nook.api.application.group.GroupView
import org.every.nook.api.application.group.port.GroupPort
import org.every.nook.api.application.member.port.MemberRepository
import org.every.nook.api.application.port.TransactionRunner
import org.every.nook.api.domain.group.GroupColor
import org.every.nook.api.domain.member.Member
import org.every.nook.api.domain.member.SocialAccount
import org.every.nook.api.domain.member.SocialProvider
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthenticateSocialUserUseCaseTest {
    private val socialIdentityProvider = FixedSocialIdentityProvider()
    private val memberRepository = AuthFakeMemberRepository()
    private val groupPort = AuthFakeGroupPort()
    private val tokenProvider = AuthFakeTokenProvider()
    private val refreshTokenRepository = AuthFakeRefreshTokenRepository()
    private val useCase = AuthenticateSocialUserUseCase(
        socialIdentityProvider = socialIdentityProvider,
        memberRepository = memberRepository,
        groupPort = groupPort,
        issueLoginTokens = IssueLoginTokens(tokenProvider, refreshTokenRepository),
        transactionRunner = AuthDirectTransactionRunner,
    )

    @Test
    fun `new social user is automatically created and signed in`() {
        val result = useCase(SocialCredential(SocialLoginProvider.KAKAO, accessToken = "provider-token"))

        assertEquals("access-1", result.tokens.accessToken)
        assertEquals("refresh-1", result.tokens.refreshToken)
        assertTrue(memberRepository.members.single().nickname.startsWith("nook"))
        assertEquals(SocialProvider.KAKAO, memberRepository.accounts.single().provider)
        assertEquals("subject", memberRepository.accounts.single().providerSubject)
        assertEquals(AuthFakeGroup(1, "내 아카이브", GroupColor.BLUE), groupPort.groups.single())
    }

    @Test
    fun `existing social user is signed in without creating a member`() {
        memberRepository.accounts += SocialAccount(
            id = 1,
            memberId = 7,
            provider = SocialProvider.KAKAO,
            providerSubject = "subject",
        )
        memberRepository.members += Member(id = 7, nickname = "기존회원", profileImageUrl = null)

        val result = useCase(SocialCredential(SocialLoginProvider.KAKAO, accessToken = "provider-token"))

        assertEquals("access-7", result.tokens.accessToken)
        assertEquals("refresh-7", result.tokens.refreshToken)
        assertEquals(1, memberRepository.members.size)
        assertTrue(groupPort.groups.isEmpty())
    }
}

private data class AuthFakeGroup(val userId: Long, val name: String, val color: GroupColor)

private object AuthDirectTransactionRunner : TransactionRunner {
    override fun <T> required(block: () -> T): T = block()
}

private class FixedSocialIdentityProvider : SocialIdentityProvider {
    override fun authenticate(credential: SocialCredential): SocialIdentity = SocialIdentity(
        provider = SocialProvider.KAKAO,
        subject = "subject",
    )
}

private class AuthFakeMemberRepository : MemberRepository {
    val members = mutableListOf<Member>()
    val accounts = mutableListOf<SocialAccount>()

    override fun findMemberId(provider: SocialProvider, subject: String): Long? =
        accounts.firstOrNull { it.provider == provider && it.providerSubject == subject }?.memberId

    override fun findById(memberId: Long): Member? = members.firstOrNull { it.id == memberId }

    override fun findSocialProvider(memberId: Long): SocialProvider? =
        accounts.firstOrNull { it.memberId == memberId }?.provider

    override fun existsByNickname(nickname: String): Boolean = members.any { it.nickname == nickname }

    override fun existsSocialAccount(provider: SocialProvider, subject: String): Boolean =
        accounts.any { it.provider == provider && it.providerSubject == subject }

    override fun save(member: Member): Member = member.copy(id = (members.size + 1).toLong()).also(members::add)

    override fun update(member: Member): Member? = member

    override fun withdraw(memberId: Long): Boolean = false

    override fun saveSocialAccount(account: SocialAccount): SocialAccount = account.copy(id = 1).also(accounts::add)

    override fun deleteSocialAccounts(memberId: Long) = Unit

    override fun existsMember(memberId: Long): Boolean = members.any { it.id == memberId }
}

private class AuthFakeGroupPort : GroupPort {
    val groups = mutableListOf<AuthFakeGroup>()

    override fun findAll(userId: Long): List<GroupView> = error("Not used")

    override fun create(userId: Long, name: String, color: GroupColor): GroupView {
        groups += AuthFakeGroup(userId, name, color)
        return GroupView(id = groups.size.toLong(), name = name, color = color.name, postCount = 0)
    }

    override fun update(userId: Long, groupId: Long, name: String, color: GroupColor): GroupPort.UpdateResult =
        error("Not used")

    override fun delete(userId: Long, groupId: Long): Boolean = error("Not used")
}

private class AuthFakeRefreshTokenRepository : RefreshTokenRepository {
    override fun save(token: StoredRefreshToken): StoredRefreshToken = token.copy(id = 1)

    override fun findByIdentifierForUpdate(identifier: String): StoredRefreshToken? = null

    override fun revoke(tokenId: Long, revokedAt: Instant, replacedByTokenId: Long) = Unit

    override fun revokeActiveTokens(memberId: Long, revokedAt: Instant) = Unit
}

private class AuthFakeTokenProvider : TokenProvider {
    override fun issueAccessToken(memberId: Long): String = "access-$memberId"

    override fun issueRefreshToken(memberId: Long): IssuedToken = IssuedToken(
        value = "refresh-$memberId",
        identifier = "identifier-$memberId",
        expiresAt = Instant.parse("2026-08-22T00:00:00Z"),
    )

    override fun parseRefreshToken(token: String): RefreshClaims = error("Not used")

    override fun hash(token: String): String = "hash-$token"
}

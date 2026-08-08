package org.every.nook.api.application.member

import org.every.nook.api.application.member.port.MemberRepository
import org.every.nook.api.application.port.TransactionRunner
import org.every.nook.api.domain.member.Member
import org.every.nook.api.domain.member.SocialAccount
import org.every.nook.api.domain.member.SocialProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MemberProfileUseCasesTest {
    private val memberRepository = ProfileFakeMemberRepository()

    @Test
    fun `gets member profile with provider`() {
        memberRepository.members += Member(id = 7, nickname = "누커", profileImageUrl = null)
        memberRepository.accounts += SocialAccount(
            id = 1,
            memberId = 7,
            provider = SocialProvider.KAKAO,
            providerSubject = "subject",
        )

        val profile = GetMemberProfileUseCase(memberRepository)(7)

        assertEquals(7, profile.id)
        assertEquals("누커", profile.nickname)
        assertEquals(MemberProvider.KAKAO, profile.provider)
    }

    @Test
    fun `throws member not found when provider does not exist`() {
        memberRepository.members += Member(id = 7, nickname = "누커", profileImageUrl = null)

        assertFailsWith<MemberNotFoundException> {
            GetMemberProfileUseCase(memberRepository)(7)
        }
    }

    @Test
    fun `updates member profile with provider`() {
        memberRepository.members += Member(id = 7, nickname = "누커", profileImageUrl = null)
        memberRepository.accounts += SocialAccount(
            id = 1,
            memberId = 7,
            provider = SocialProvider.KAKAO,
            providerSubject = "subject",
        )

        val profile = UpdateMemberProfileUseCase(memberRepository, ProfileDirectTransactionRunner)(
            UpdateMemberProfileCommand(
                memberId = 7,
                nickname = "도현",
                profileImageUrl = "https://example.com/profile.jpg",
            ),
        )

        assertEquals("도현", profile.nickname)
        assertEquals("https://example.com/profile.jpg", profile.profileImageUrl)
        assertEquals(MemberProvider.KAKAO, profile.provider)
    }
}

private object ProfileDirectTransactionRunner : TransactionRunner {
    override fun <T> required(block: () -> T): T = block()
}

private class ProfileFakeMemberRepository : MemberRepository {
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

    override fun update(member: Member): Member? {
        val index = members.indexOfFirst { it.id == member.id }
        if (index < 0) return null
        members[index] = member
        return member
    }

    override fun withdraw(memberId: Long): Boolean = members.removeIf { it.id == memberId }

    override fun saveSocialAccount(account: SocialAccount): SocialAccount = account.copy(id = 1).also(accounts::add)

    override fun deleteSocialAccounts(memberId: Long) {
        accounts.removeIf { it.memberId == memberId }
    }

    override fun existsMember(memberId: Long): Boolean = members.any { it.id == memberId }
}

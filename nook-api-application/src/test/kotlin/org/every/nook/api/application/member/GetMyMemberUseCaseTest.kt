package org.every.nook.api.application.member

import org.every.nook.api.application.member.port.MemberProfile
import org.every.nook.api.application.member.port.MemberRepository
import org.every.nook.api.domain.member.Member
import org.every.nook.api.domain.member.SocialAccount
import org.every.nook.api.domain.member.SocialProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GetMyMemberUseCaseTest {
    private val memberRepository = StubMemberRepository()
    private val useCase = GetMyMemberUseCase(memberRepository)

    @Test
    fun `returns my member profile with provider`() {
        memberRepository.profile = MemberProfile(
            member = Member(
                id = 7,
                nickname = "누커",
                profileImageUrl = "https://example.com/profile.png",
            ),
            provider = SocialProvider.KAKAO,
        )

        val result = useCase(7)

        assertEquals(7, result.id)
        assertEquals("누커", result.nickname)
        assertEquals("https://example.com/profile.png", result.profileImageUrl)
        assertEquals(MemberProvider.KAKAO, result.provider)
    }

    @Test
    fun `throws member not found when profile does not exist`() {
        assertFailsWith<MemberNotFoundException> {
            useCase(404)
        }
        assertNull(memberRepository.profile)
    }
}

private class StubMemberRepository : MemberRepository {
    var profile: MemberProfile? = null

    override fun findMemberId(provider: SocialProvider, subject: String): Long? = null

    override fun findMemberProfile(memberId: Long): MemberProfile? = profile

    override fun existsByNickname(nickname: String): Boolean = false

    override fun existsSocialAccount(provider: SocialProvider, subject: String): Boolean = false

    override fun save(member: Member): Member = error("Not used")

    override fun saveSocialAccount(account: SocialAccount): SocialAccount = error("Not used")

    override fun existsMember(memberId: Long): Boolean = false
}

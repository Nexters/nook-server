package org.every.nook.api.infrastructure.persistence.member

import org.every.nook.api.domain.member.MemberStatus
import org.every.nook.api.domain.member.SocialProvider
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemberRepositoryAdapterTest {
    private val memberJpaRepository = mock(MemberJpaRepository::class.java)
    private val socialAccountJpaRepository = mock(SocialAccountJpaRepository::class.java)
    private val adapter = MemberRepositoryAdapter(memberJpaRepository, socialAccountJpaRepository)

    @Test
    fun `finds member profile with social provider`() {
        val member = memberEntity(
            id = 7,
            nickname = "누커",
            profileImageUrl = "https://example.com/profile.png",
        )
        val account = socialAccountEntity(
            id = 11,
            member = member,
            provider = SocialProvider.KAKAO,
            providerSubject = "kakao-subject",
        )
        `when`(memberJpaRepository.findById(7)).thenReturn(Optional.of(member))
        `when`(socialAccountJpaRepository.findFirstByMemberId(7)).thenReturn(account)

        val profile = adapter.findMemberProfile(7)

        assertEquals(7, profile?.member?.id)
        assertEquals("누커", profile?.member?.nickname)
        assertEquals("https://example.com/profile.png", profile?.member?.profileImageUrl)
        assertEquals(SocialProvider.KAKAO, profile?.provider)
    }

    @Test
    fun `returns null when member does not exist`() {
        `when`(memberJpaRepository.findById(404)).thenReturn(Optional.empty())

        assertNull(adapter.findMemberProfile(404))
        verify(memberJpaRepository).findById(404)
    }

    @Test
    fun `returns null when social account does not exist`() {
        val member = memberEntity(id = 7, nickname = "누커", profileImageUrl = null)
        `when`(memberJpaRepository.findById(7)).thenReturn(Optional.of(member))
        `when`(socialAccountJpaRepository.findFirstByMemberId(7)).thenReturn(null)

        assertNull(adapter.findMemberProfile(7))
    }

    private fun memberEntity(id: Long, nickname: String, profileImageUrl: String?): MemberEntity = MemberEntity(
        nickname = nickname,
        profileImageUrl = profileImageUrl,
        status = MemberStatus.ACTIVE,
    ).also {
        ReflectionTestUtils.setField(it, "id", id)
    }

    private fun socialAccountEntity(
        id: Long,
        member: MemberEntity,
        provider: SocialProvider,
        providerSubject: String,
    ): SocialAccountEntity = SocialAccountEntity(
        member = member,
        provider = provider,
        providerSubject = providerSubject,
    ).also {
        ReflectionTestUtils.setField(it, "id", id)
    }
}

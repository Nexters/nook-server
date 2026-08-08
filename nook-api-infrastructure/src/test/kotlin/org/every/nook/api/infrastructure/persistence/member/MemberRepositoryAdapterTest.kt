package org.every.nook.api.infrastructure.persistence.member

import org.every.nook.api.domain.member.MemberStatus
import org.every.nook.api.domain.member.SocialProvider
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.test.util.ReflectionTestUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemberRepositoryAdapterTest {
    private val memberJpaRepository = mock(MemberJpaRepository::class.java)
    private val socialAccountJpaRepository = mock(SocialAccountJpaRepository::class.java)
    private val adapter = MemberRepositoryAdapter(memberJpaRepository, socialAccountJpaRepository)

    @Test
    fun `finds active member social provider`() {
        val member = memberEntity(id = 7, status = MemberStatus.ACTIVE)
        val account = socialAccountEntity(
            id = 11,
            member = member,
            provider = SocialProvider.KAKAO,
            providerSubject = "kakao-subject",
        )
        `when`(socialAccountJpaRepository.findFirstByMemberId(7)).thenReturn(account)

        assertEquals(SocialProvider.KAKAO, adapter.findSocialProvider(7))
    }

    @Test
    fun `returns null when social account does not exist`() {
        `when`(socialAccountJpaRepository.findFirstByMemberId(7)).thenReturn(null)

        assertNull(adapter.findSocialProvider(7))
    }

    @Test
    fun `returns null when member is withdrawn`() {
        val member = memberEntity(id = 7, status = MemberStatus.WITHDRAWN)
        val account = socialAccountEntity(
            id = 11,
            member = member,
            provider = SocialProvider.KAKAO,
            providerSubject = "kakao-subject",
        )
        `when`(socialAccountJpaRepository.findFirstByMemberId(7)).thenReturn(account)

        assertNull(adapter.findSocialProvider(7))
    }

    private fun memberEntity(id: Long, status: MemberStatus): MemberEntity = MemberEntity(
        nickname = "누커",
        profileImageUrl = null,
        status = status,
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

package org.every.nook.api.infrastructure.persistence.member

import org.every.nook.api.domain.member.MemberStatus
import org.every.nook.api.domain.member.SocialProvider
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemberRepositoryAdapterTest {
    private val memberJpaRepository = mock(MemberJpaRepository::class.java)
    private val socialAccountJpaRepository = mock(SocialAccountJpaRepository::class.java)
    private val adapter = MemberRepositoryAdapter(memberJpaRepository, socialAccountJpaRepository)

    @Test
    fun `finds active member social provider`() {
        `when`(socialAccountJpaRepository.findActiveProviders(7, MemberStatus.ACTIVE))
            .thenReturn(listOf(SocialProvider.KAKAO))

        assertEquals(SocialProvider.KAKAO, adapter.findSocialProvider(7))
    }

    @Test
    fun `returns null when social account does not exist`() {
        `when`(socialAccountJpaRepository.findActiveProviders(7, MemberStatus.ACTIVE)).thenReturn(emptyList())

        assertNull(adapter.findSocialProvider(7))
    }

    @Test
    fun `finds active member id by social identity`() {
        `when`(
            socialAccountJpaRepository.findActiveMemberId(
                SocialProvider.KAKAO,
                "kakao-subject",
                MemberStatus.ACTIVE,
            ),
        ).thenReturn(7)

        assertEquals(7, adapter.findMemberId(SocialProvider.KAKAO, "kakao-subject"))
    }

    @Test
    fun `returns null when active member does not match social identity`() {
        `when`(
            socialAccountJpaRepository.findActiveMemberId(
                SocialProvider.KAKAO,
                "kakao-subject",
                MemberStatus.ACTIVE,
            ),
        ).thenReturn(null)

        assertNull(adapter.findMemberId(SocialProvider.KAKAO, "kakao-subject"))
    }
}

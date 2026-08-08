package org.every.nook.api.infrastructure.persistence.member

import org.every.nook.api.domain.member.SocialProvider
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MemberJpaRepository : JpaRepository<MemberEntity, Long> {
    fun existsByNickname(nickname: String): Boolean

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
            update MemberEntity member
            set member.status = org.every.nook.api.domain.member.MemberStatus.WITHDRAWN
            where member.id = :memberId
              and member.status = org.every.nook.api.domain.member.MemberStatus.ACTIVE
        """,
    )
    fun withdraw(@Param("memberId") memberId: Long): Int
}

interface SocialAccountJpaRepository : JpaRepository<SocialAccountEntity, Long> {
    fun findByProviderAndProviderSubject(provider: SocialProvider, providerSubject: String): SocialAccountEntity?

    fun findFirstByMemberId(memberId: Long): SocialAccountEntity?

    fun existsByProviderAndProviderSubject(provider: SocialProvider, providerSubject: String): Boolean

    fun deleteAllByMemberId(memberId: Long)
}

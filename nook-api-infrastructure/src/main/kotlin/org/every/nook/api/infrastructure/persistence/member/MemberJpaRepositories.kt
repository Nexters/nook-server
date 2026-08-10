package org.every.nook.api.infrastructure.persistence.member

import org.every.nook.api.domain.member.MemberStatus
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
    @Query(
        """
            select member.id
            from SocialAccountEntity account
            join account.member member
            where account.provider = :provider
              and account.providerSubject = :providerSubject
              and member.status = :status
        """,
    )
    fun findActiveMemberId(
        @Param("provider") provider: SocialProvider,
        @Param("providerSubject") providerSubject: String,
        @Param("status") status: MemberStatus,
    ): Long?

    @Query(
        """
            select account.provider
            from SocialAccountEntity account
            join account.member member
            where member.id = :memberId
              and member.status = :status
            order by account.id asc
        """,
    )
    fun findActiveProviders(
        @Param("memberId") memberId: Long,
        @Param("status") status: MemberStatus,
    ): List<SocialProvider>

    fun existsByProviderAndProviderSubject(provider: SocialProvider, providerSubject: String): Boolean

    fun deleteAllByMemberId(memberId: Long)
}

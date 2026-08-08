package org.every.nook.api.infrastructure.persistence.member

import org.every.nook.api.domain.member.SocialProvider
import org.springframework.data.jpa.repository.JpaRepository

interface MemberJpaRepository : JpaRepository<MemberEntity, Long> {
    fun existsByNickname(nickname: String): Boolean
}

interface SocialAccountJpaRepository : JpaRepository<SocialAccountEntity, Long> {
    fun findByProviderAndProviderSubject(provider: SocialProvider, providerSubject: String): SocialAccountEntity?

    fun findFirstByMemberId(memberId: Long): SocialAccountEntity?

    fun existsByProviderAndProviderSubject(provider: SocialProvider, providerSubject: String): Boolean
}

package org.every.nook.api.infrastructure.persistence.member

import org.every.nook.api.application.member.DuplicateNicknameException
import org.every.nook.api.application.member.DuplicateSocialAccountException
import org.every.nook.api.application.member.port.MemberRepository
import org.every.nook.api.domain.member.Member
import org.every.nook.api.domain.member.SocialAccount
import org.every.nook.api.domain.member.SocialProvider
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository

@Repository
class MemberRepositoryAdapter(
    private val memberJpaRepository: MemberJpaRepository,
    private val socialAccountJpaRepository: SocialAccountJpaRepository,
) : MemberRepository {
    override fun findMemberId(provider: SocialProvider, subject: String): Long? =
        socialAccountJpaRepository.findByProviderAndProviderSubject(provider, subject)?.member?.id

    override fun existsByNickname(nickname: String): Boolean = memberJpaRepository.existsByNickname(nickname)

    override fun existsSocialAccount(provider: SocialProvider, subject: String): Boolean =
        socialAccountJpaRepository.existsByProviderAndProviderSubject(provider, subject)

    override fun save(member: Member): Member = try {
        memberJpaRepository.saveAndFlush(MemberEntity.from(member)).toDomain()
    } catch (exception: DataIntegrityViolationException) {
        throw DuplicateNicknameException(exception)
    }

    override fun saveSocialAccount(account: SocialAccount): SocialAccount {
        val member = memberJpaRepository.getReferenceById(account.memberId)
        return try {
            socialAccountJpaRepository.saveAndFlush(
                SocialAccountEntity(
                    member = member,
                    provider = account.provider,
                    providerSubject = account.providerSubject,
                ),
            ).toDomain()
        } catch (exception: DataIntegrityViolationException) {
            throw DuplicateSocialAccountException(exception)
        }
    }

    override fun existsMember(memberId: Long): Boolean = memberJpaRepository.existsById(memberId)
}

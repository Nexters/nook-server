package org.every.nook.api.application.auth

import org.every.nook.api.application.auth.port.SocialIdentityProvider
import org.every.nook.api.application.member.DuplicateSocialAccountException
import org.every.nook.api.application.member.port.MemberRepository
import org.every.nook.api.application.port.TransactionRunner
import org.every.nook.api.domain.member.Member
import org.every.nook.api.domain.member.SocialAccount
import java.util.UUID

class AuthenticateSocialUserUseCase(
    private val socialIdentityProvider: SocialIdentityProvider,
    private val memberRepository: MemberRepository,
    private val issueLoginTokens: IssueLoginTokens,
    private val transactionRunner: TransactionRunner,
) {
    operator fun invoke(credential: SocialCredential): SocialAuthenticationResult {
        val identity = socialIdentityProvider.authenticate(credential)
        return transactionRunner.required {
            val memberId = memberRepository.findMemberId(identity.provider, identity.subject)
                ?: createMember(identity)
            SocialAuthenticationResult(issueLoginTokens(memberId))
        }
    }

    private fun createMember(identity: SocialIdentity): Long {
        if (memberRepository.existsSocialAccount(identity.provider, identity.subject)) {
            throw DuplicateSocialAccountException()
        }
        val member = memberRepository.save(
            Member(
                nickname = generateDefaultNickname(),
                profileImageUrl = null,
            ),
        )
        val memberId = requireNotNull(member.id)
        memberRepository.saveSocialAccount(
            SocialAccount(
                memberId = memberId,
                provider = identity.provider,
                providerSubject = identity.subject,
            ),
        )
        return memberId
    }

    private fun generateDefaultNickname(): String {
        val suffix = UUID.randomUUID().toString().take(DEFAULT_NICKNAME_SUFFIX_LENGTH)
        return "nook" + suffix
    }

    private companion object {
        const val DEFAULT_NICKNAME_SUFFIX_LENGTH = 8
    }
}

package org.every.nook.api.application.member

import org.every.nook.api.application.auth.IssueLoginTokens
import org.every.nook.api.application.auth.LoginTokens
import org.every.nook.api.application.auth.port.TokenProvider
import org.every.nook.api.application.member.port.MemberRepository
import org.every.nook.api.application.port.TransactionRunner
import org.every.nook.api.domain.member.Member
import org.every.nook.api.domain.member.SocialAccount

data class SignupMemberCommand(val signupToken: String, val nickname: String, val profileImageUrl: String?)

class SignupMemberUseCase(
    private val tokenProvider: TokenProvider,
    private val memberRepository: MemberRepository,
    private val issueLoginTokens: IssueLoginTokens,
    private val transactionRunner: TransactionRunner,
) {
    operator fun invoke(command: SignupMemberCommand): LoginTokens {
        val claims = tokenProvider.parseSignupToken(command.signupToken)
        val nickname = Member.normalizeNickname(command.nickname)
        return transactionRunner.required {
            if (memberRepository.existsByNickname(nickname)) throw DuplicateNicknameException()
            if (memberRepository.existsSocialAccount(claims.provider, claims.subject)) {
                throw DuplicateSocialAccountException()
            }
            val member = memberRepository.save(
                Member(nickname = nickname, profileImageUrl = command.profileImageUrl),
            )
            val memberId = requireNotNull(member.id)
            memberRepository.saveSocialAccount(
                SocialAccount(
                    memberId = memberId,
                    provider = claims.provider,
                    providerSubject = claims.subject,
                ),
            )
            issueLoginTokens(memberId)
        }
    }
}

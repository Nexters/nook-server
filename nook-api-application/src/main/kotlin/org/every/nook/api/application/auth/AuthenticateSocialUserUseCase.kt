package org.every.nook.api.application.auth

import org.every.nook.api.application.auth.port.SocialIdentityProvider
import org.every.nook.api.application.auth.port.TokenProvider
import org.every.nook.api.application.member.port.MemberRepository
import org.every.nook.api.application.port.TransactionRunner

class AuthenticateSocialUserUseCase(
    private val socialIdentityProvider: SocialIdentityProvider,
    private val memberRepository: MemberRepository,
    private val tokenProvider: TokenProvider,
    private val issueLoginTokens: IssueLoginTokens,
    private val transactionRunner: TransactionRunner,
) {
    operator fun invoke(credential: SocialCredential): SocialAuthenticationResult {
        val identity = socialIdentityProvider.authenticate(credential)
        val memberId = memberRepository.findMemberId(identity.provider, identity.subject)
        return if (memberId == null) {
            SocialAuthenticationResult.SignupRequired(
                tokenProvider.issueSignupToken(identity.provider, identity.subject),
            )
        } else {
            transactionRunner.required {
                SocialAuthenticationResult.SignedIn(issueLoginTokens(memberId))
            }
        }
    }
}

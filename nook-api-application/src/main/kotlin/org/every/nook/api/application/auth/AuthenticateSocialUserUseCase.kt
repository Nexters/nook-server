package org.every.nook.api.application.auth

import org.every.nook.api.application.analytics.UserAnalyticsEventName
import org.every.nook.api.application.analytics.UserAnalyticsEventRecorder
import org.every.nook.api.application.analytics.UserAnalyticsRecord
import org.every.nook.api.application.auth.port.SocialIdentityProvider
import org.every.nook.api.application.group.port.GroupPort
import org.every.nook.api.application.member.DuplicateSocialAccountException
import org.every.nook.api.application.member.port.MemberRepository
import org.every.nook.api.application.port.TransactionRunner
import org.every.nook.api.domain.group.GroupColor
import org.every.nook.api.domain.member.Member
import org.every.nook.api.domain.member.SocialAccount
import java.util.UUID

class AuthenticateSocialUserUseCase(
    private val socialIdentityProvider: SocialIdentityProvider,
    private val memberRepository: MemberRepository,
    private val groupPort: GroupPort,
    private val issueLoginTokens: IssueLoginTokens,
    private val transactionRunner: TransactionRunner,
    private val analyticsRecorder: UserAnalyticsEventRecorder = UserAnalyticsEventRecorder.NONE,
) {
    operator fun invoke(credential: SocialCredential): SocialAuthenticationResult {
        val identity = socialIdentityProvider.authenticate(credential)
        val authentication = transactionRunner.required {
            val existingMemberId = memberRepository.findMemberId(identity.provider, identity.subject)
            val memberId = existingMemberId ?: createMember(identity)
            Authentication(
                result = SocialAuthenticationResult(issueLoginTokens(memberId)),
                memberId = memberId,
                signedUp = existingMemberId == null,
            )
        }
        if (authentication.signedUp) {
            analyticsRecorder.record(
                UserAnalyticsRecord(
                    eventName = UserAnalyticsEventName.SIGN_UP,
                    memberId = authentication.memberId,
                ),
            )
        }
        return authentication.result
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
        groupPort.create(memberId, DEFAULT_GROUP_NAME, DEFAULT_GROUP_COLOR)
        return memberId
    }

    private fun generateDefaultNickname(): String {
        val suffix = UUID.randomUUID().toString().take(DEFAULT_NICKNAME_SUFFIX_LENGTH)
        return "nook" + suffix
    }

    private data class Authentication(
        val result: SocialAuthenticationResult,
        val memberId: Long,
        val signedUp: Boolean,
    )

    private companion object {
        const val DEFAULT_NICKNAME_SUFFIX_LENGTH = 8
        const val DEFAULT_GROUP_NAME = "내 아카이브"
        val DEFAULT_GROUP_COLOR = GroupColor.BLUE
    }
}

package org.every.nook.api.application.member

import org.every.nook.api.application.auth.port.RefreshTokenRepository
import org.every.nook.api.application.member.port.MemberRepository
import org.every.nook.api.application.port.TransactionRunner
import org.every.nook.api.domain.member.Member
import org.every.nook.api.domain.member.SocialProvider
import java.time.Clock
import java.time.Instant

enum class MemberProvider {
    KAKAO,
    GOOGLE,
    APPLE,
    ;

    companion object {
        fun from(provider: SocialProvider): MemberProvider = valueOf(provider.name)
    }
}

data class MemberProfile(
    val id: Long,
    val nickname: String,
    val profileImageUrl: String?,
    val provider: MemberProvider,
)

data class UpdateMemberProfileCommand(val memberId: Long, val nickname: String, val profileImageUrl: String?)

class GetMemberProfileUseCase(private val memberRepository: MemberRepository) {
    operator fun invoke(memberId: Long): MemberProfile {
        val member = memberRepository.findById(memberId) ?: throw MemberNotFoundException()
        val provider = memberRepository.findSocialProvider(memberId) ?: throw MemberNotFoundException()
        return member.toProfile(provider)
    }
}

class UpdateMemberProfileUseCase(
    private val memberRepository: MemberRepository,
    private val transactionRunner: TransactionRunner,
) {
    operator fun invoke(command: UpdateMemberProfileCommand): MemberProfile {
        val nickname = Member.normalizeNickname(command.nickname)
        return transactionRunner.required {
            val current = memberRepository.findById(command.memberId) ?: memberNotFound()
            val provider = memberRepository.findSocialProvider(command.memberId) ?: memberNotFound()
            val updated = memberRepository.update(
                current.copy(
                    nickname = nickname,
                    profileImageUrl = command.profileImageUrl,
                ),
            ) ?: memberNotFound()
            updated.toProfile(provider)
        }
    }
}

class LogoutMemberUseCase(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    operator fun invoke(memberId: Long) {
        val now = Instant.now(clock)
        transactionRunner.required {
            refreshTokenRepository.revokeActiveTokens(memberId, now)
        }
    }
}

class WithdrawMemberUseCase(
    private val memberRepository: MemberRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    operator fun invoke(memberId: Long) {
        val now = Instant.now(clock)
        transactionRunner.required {
            if (!memberRepository.withdraw(memberId)) throw MemberNotFoundException()
            memberRepository.deleteSocialAccounts(memberId)
            refreshTokenRepository.revokeActiveTokens(memberId, now)
        }
    }
}

private fun Member.toProfile(provider: SocialProvider): MemberProfile = MemberProfile(
    id = requireNotNull(id),
    nickname = nickname,
    profileImageUrl = profileImageUrl,
    provider = MemberProvider.from(provider),
)

private fun memberNotFound(): Nothing = throw MemberNotFoundException()

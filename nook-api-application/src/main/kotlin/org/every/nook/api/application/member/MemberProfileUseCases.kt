package org.every.nook.api.application.member

import org.every.nook.api.application.auth.port.RefreshTokenRepository
import org.every.nook.api.application.member.port.MemberRepository
import org.every.nook.api.application.port.TransactionRunner
import org.every.nook.api.domain.member.Member
import java.time.Clock
import java.time.Instant

data class MemberProfile(val id: Long, val nickname: String, val profileImageUrl: String?)

data class UpdateMemberProfileCommand(val memberId: Long, val nickname: String, val profileImageUrl: String?)

class GetMemberProfileUseCase(private val memberRepository: MemberRepository) {
    operator fun invoke(memberId: Long): MemberProfile {
        val member = memberRepository.findById(memberId) ?: throw MemberNotFoundException()
        return member.toProfile()
    }
}

class UpdateMemberProfileUseCase(
    private val memberRepository: MemberRepository,
    private val transactionRunner: TransactionRunner,
) {
    operator fun invoke(command: UpdateMemberProfileCommand): MemberProfile {
        val nickname = Member.normalizeNickname(command.nickname)
        return transactionRunner.required {
            val current = memberRepository.findById(command.memberId) ?: throw MemberNotFoundException()
            val updated = memberRepository.update(
                current.copy(
                    nickname = nickname,
                    profileImageUrl = command.profileImageUrl,
                ),
            ) ?: throw MemberNotFoundException()
            updated.toProfile()
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

private fun Member.toProfile(): MemberProfile = MemberProfile(
    id = requireNotNull(id),
    nickname = nickname,
    profileImageUrl = profileImageUrl,
)

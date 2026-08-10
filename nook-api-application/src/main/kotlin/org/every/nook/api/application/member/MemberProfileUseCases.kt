package org.every.nook.api.application.member

import org.every.nook.api.application.auth.port.RefreshTokenRepository
import org.every.nook.api.application.member.port.MemberRepository
import org.every.nook.api.application.member.port.ProfileImageUpload
import org.every.nook.api.application.member.port.ProfileImageUploadCommand
import org.every.nook.api.application.member.port.ProfileImageUploadPort
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

data class UpdateMemberProfileCommand(
    val memberId: Long,
    val nickname: String?,
    val profileImageUrl: ProfileImageUrlUpdate = ProfileImageUrlUpdate.Unchanged,
)

sealed interface ProfileImageUrlUpdate {
    data object Unchanged : ProfileImageUrlUpdate
    data class Replace(val value: String?) : ProfileImageUrlUpdate
}

data class CreateProfileImageUploadCommand(val memberId: Long, val contentType: String)

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
    operator fun invoke(command: UpdateMemberProfileCommand): MemberProfile = transactionRunner.required {
        val current = memberRepository.findById(command.memberId) ?: memberNotFound()
        val provider = memberRepository.findSocialProvider(command.memberId) ?: memberNotFound()
        val updated = memberRepository.update(
            current.copy(
                nickname = command.nickname?.let(Member::normalizeNickname) ?: current.nickname,
                profileImageUrl = command.profileImageUrl.updatedValue(current.profileImageUrl),
            ),
        ) ?: memberNotFound()
        updated.toProfile(provider)
    }
}

class CreateProfileImageUploadUseCase(
    private val memberRepository: MemberRepository,
    private val profileImageUploadPort: ProfileImageUploadPort,
) {
    operator fun invoke(command: CreateProfileImageUploadCommand): ProfileImageUpload {
        if (!memberRepository.existsMember(command.memberId)) throw MemberNotFoundException()
        return profileImageUploadPort.create(
            ProfileImageUploadCommand(
                memberId = command.memberId,
                contentType = command.contentType,
            ),
        )
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

private fun ProfileImageUrlUpdate.updatedValue(current: String?): String? = when (this) {
    ProfileImageUrlUpdate.Unchanged -> current
    is ProfileImageUrlUpdate.Replace -> value
}

private fun memberNotFound(): Nothing = throw MemberNotFoundException()

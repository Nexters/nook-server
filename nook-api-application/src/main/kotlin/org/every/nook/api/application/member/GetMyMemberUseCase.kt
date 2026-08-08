package org.every.nook.api.application.member

import org.every.nook.api.application.member.port.MemberRepository
import org.every.nook.api.domain.member.SocialProvider

enum class MemberProvider {
    KAKAO,
    GOOGLE,
    APPLE,
    ;

    companion object {
        fun from(provider: SocialProvider): MemberProvider = valueOf(provider.name)
    }
}

class GetMyMemberUseCase(private val memberRepository: MemberRepository) {
    operator fun invoke(memberId: Long): Result {
        val profile = memberRepository.findMemberProfile(memberId) ?: throw MemberNotFoundException()
        return Result(
            id = requireNotNull(profile.member.id),
            nickname = profile.member.nickname,
            profileImageUrl = profile.member.profileImageUrl,
            provider = MemberProvider.from(profile.provider),
        )
    }

    data class Result(val id: Long, val nickname: String, val profileImageUrl: String?, val provider: MemberProvider)
}

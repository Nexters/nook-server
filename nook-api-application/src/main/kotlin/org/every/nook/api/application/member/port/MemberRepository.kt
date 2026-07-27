package org.every.nook.api.application.member.port

import org.every.nook.api.domain.member.Member
import org.every.nook.api.domain.member.SocialAccount
import org.every.nook.api.domain.member.SocialProvider

interface MemberRepository {
    fun findMemberId(provider: SocialProvider, subject: String): Long?

    fun existsByNickname(nickname: String): Boolean

    fun existsSocialAccount(provider: SocialProvider, subject: String): Boolean

    fun save(member: Member): Member

    fun saveSocialAccount(account: SocialAccount): SocialAccount

    fun existsMember(memberId: Long): Boolean
}

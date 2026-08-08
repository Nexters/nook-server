package org.every.nook.api.application.member.port

import org.every.nook.api.domain.member.Member
import org.every.nook.api.domain.member.SocialAccount
import org.every.nook.api.domain.member.SocialProvider

interface MemberRepository {
    fun findMemberId(provider: SocialProvider, subject: String): Long?

    fun findById(memberId: Long): Member?

    fun findSocialProvider(memberId: Long): SocialProvider?

    fun existsByNickname(nickname: String): Boolean

    fun existsSocialAccount(provider: SocialProvider, subject: String): Boolean

    fun save(member: Member): Member

    fun update(member: Member): Member?

    fun withdraw(memberId: Long): Boolean

    fun saveSocialAccount(account: SocialAccount): SocialAccount

    fun deleteSocialAccounts(memberId: Long)

    fun existsMember(memberId: Long): Boolean
}

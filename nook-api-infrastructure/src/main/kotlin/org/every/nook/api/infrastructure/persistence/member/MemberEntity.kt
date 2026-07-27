package org.every.nook.api.infrastructure.persistence.member

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.domain.member.Member
import org.every.nook.api.domain.member.MemberStatus
import org.every.nook.api.infrastructure.persistence.BaseEntity

@Entity
@Table(
    name = "members",
    uniqueConstraints = [UniqueConstraint(name = "idx_u_nickname", columnNames = ["nickname"])],
)
class MemberEntity(
    @Column(name = "nickname", nullable = false, length = 20)
    var nickname: String,
    @Column(name = "profile_image_url", nullable = true, length = 2048)
    var profileImageUrl: String?,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: MemberStatus,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set

    fun toDomain(): Member = Member(
        id = id,
        nickname = nickname,
        profileImageUrl = profileImageUrl,
        status = status,
    )

    companion object {
        fun from(member: Member): MemberEntity = MemberEntity(
            nickname = member.nickname,
            profileImageUrl = member.profileImageUrl,
            status = member.status,
        )
    }
}

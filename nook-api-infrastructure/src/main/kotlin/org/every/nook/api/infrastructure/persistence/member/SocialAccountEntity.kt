package org.every.nook.api.infrastructure.persistence.member

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.domain.member.SocialAccount
import org.every.nook.api.domain.member.SocialProvider
import org.every.nook.api.infrastructure.persistence.BaseEntity

@Entity
@Table(
    name = "social_accounts",
    indexes = [Index(name = "idx_member_id", columnList = "member_id")],
    uniqueConstraints = [
        UniqueConstraint(
            name = "idx_u_provider_provider_subject",
            columnNames = ["provider", "provider_subject"],
        ),
    ],
)
class SocialAccountEntity(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    var member: MemberEntity,
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    var provider: SocialProvider,
    @Column(name = "provider_subject", nullable = false, length = 255)
    var providerSubject: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set

    fun toDomain(): SocialAccount = SocialAccount(
        id = id,
        memberId = requireNotNull(member.id),
        provider = provider,
        providerSubject = providerSubject,
    )
}

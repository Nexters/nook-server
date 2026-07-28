package org.every.nook.api.infrastructure.persistence.auth

import jakarta.persistence.Column
import jakarta.persistence.ConstraintMode
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.application.auth.port.StoredRefreshToken
import org.every.nook.api.infrastructure.persistence.BaseEntity
import org.every.nook.api.infrastructure.persistence.member.MemberEntity
import java.time.Instant

@Entity
@Table(
    name = "refresh_tokens",
    indexes = [
        Index(name = "idx_member_id", columnList = "member_id"),
        Index(name = "idx_replaced_by_token_id", columnList = "replaced_by_token_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "idx_u_token_identifier", columnNames = ["token_identifier"]),
        UniqueConstraint(name = "idx_u_token_hash", columnNames = ["token_hash"]),
    ],
)
class RefreshTokenEntity(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "member_id",
        nullable = false,
        foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT),
    )
    var member: MemberEntity,
    @Column(name = "token_identifier", nullable = false, length = 36)
    var tokenIdentifier: String,
    @Column(name = "token_hash", nullable = false, length = 64)
    var tokenHash: String,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,
    @Column(name = "revoked_at", nullable = true)
    var revokedAt: Instant? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "replaced_by_token_id",
        nullable = true,
        foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT),
    )
    var replacedByToken: RefreshTokenEntity? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set

    fun toStored(): StoredRefreshToken = StoredRefreshToken(
        id = id,
        memberId = requireNotNull(member.id),
        tokenIdentifier = tokenIdentifier,
        tokenHash = tokenHash,
        expiresAt = expiresAt,
        revokedAt = revokedAt,
        replacedByTokenId = replacedByToken?.id,
    )
}

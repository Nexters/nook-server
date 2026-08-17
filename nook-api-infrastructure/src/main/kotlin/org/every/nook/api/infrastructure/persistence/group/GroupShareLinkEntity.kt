package org.every.nook.api.infrastructure.persistence.group

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.infrastructure.persistence.BaseEntity
import java.time.Instant

@Entity
@Table(
    name = "group_share_links",
    indexes = [Index(name = "idx_group_id_revoked_at", columnList = "group_id, revoked_at")],
    uniqueConstraints = [UniqueConstraint(name = "idx_u_token_hash", columnNames = ["token_hash"])],
)
class GroupShareLinkEntity(
    @Column(name = "group_id", nullable = false)
    val groupId: Long,
    @Column(name = "token_hash", nullable = false, length = 64)
    val tokenHash: String,
    @Column(name = "token_value", nullable = false, length = 128)
    val tokenValue: String,
    @Column(name = "expires_at", nullable = true)
    val expiresAt: Instant?,
) : BaseEntity() {
    @Column(name = "revoked_at", nullable = true)
    var revokedAt: Instant? = null
        protected set

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set

    fun revoke(now: Instant) {
        if (revokedAt == null) revokedAt = now
    }
}

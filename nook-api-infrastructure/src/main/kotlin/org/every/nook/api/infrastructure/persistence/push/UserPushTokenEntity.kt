package org.every.nook.api.infrastructure.persistence.push

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.application.push.PushPlatform
import org.every.nook.api.infrastructure.persistence.BaseEntity
import java.time.Instant

@Entity
@Table(
    name = "user_push_tokens",
    indexes = [
        Index(name = "idx_user_id", columnList = "user_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "idx_u_token", columnNames = ["token"]),
    ],
)
class UserPushTokenEntity(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "token", nullable = false, length = MAX_TOKEN_LENGTH)
    val token: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = MAX_PLATFORM_LENGTH)
    var platform: PushPlatform,
    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,
    @Column(name = "last_registered_at", nullable = false)
    var lastRegisteredAt: Instant,
    @Column(name = "last_failed_at", nullable = true)
    var lastFailedAt: Instant? = null,
    @Column(name = "failure_reason", nullable = true, length = MAX_FAILURE_REASON_LENGTH)
    var failureReason: String? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set

    fun register(userId: Long, platform: PushPlatform, now: Instant) {
        this.userId = userId
        this.platform = platform
        enabled = true
        lastRegisteredAt = now
        lastFailedAt = null
        failureReason = null
    }

    fun disable(reason: String, now: Instant) {
        enabled = false
        lastFailedAt = now
        failureReason = reason.take(MAX_FAILURE_REASON_LENGTH)
    }

    companion object {
        const val MAX_TOKEN_LENGTH = 512
        const val MAX_PLATFORM_LENGTH = 20
        const val MAX_FAILURE_REASON_LENGTH = 500
    }
}

package org.every.nook.api.infrastructure.persistence.analytics

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
import org.every.nook.api.application.analytics.UserAnalyticsEventName
import org.every.nook.api.application.analytics.UserAnalyticsTargetType
import org.every.nook.api.infrastructure.persistence.BaseEntity
import java.time.Instant

@Entity
@Table(
    name = "analytics_events",
    indexes = [
        Index(name = "idx_event_name_occurred_at", columnList = "event_name, occurred_at"),
        Index(
            name = "idx_member_id_occurred_at_event_name",
            columnList = "member_id, occurred_at, event_name",
        ),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "idx_u_event_id", columnNames = ["event_id"]),
        UniqueConstraint(name = "idx_u_deduplication_key", columnNames = ["deduplication_key"]),
    ],
)
class UserAnalyticsEventEntity(
    @Column(name = "event_id", nullable = false, length = 36, updatable = false)
    val eventId: String,
    @Column(name = "deduplication_key", nullable = false, length = 191, updatable = false)
    val deduplicationKey: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "event_name", nullable = false, length = 30, updatable = false)
    val eventName: UserAnalyticsEventName,
    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = true, length = 20, updatable = false)
    val targetType: UserAnalyticsTargetType?,
    @Column(name = "target_id", nullable = true, updatable = false)
    val targetId: Long?,
    @Column(name = "occurred_at", nullable = false, updatable = false)
    val occurredAt: Instant,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}

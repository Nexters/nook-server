package org.every.nook.api.infrastructure.persistence.providerusage

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.every.nook.api.infrastructure.persistence.BaseEntity
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "external_provider_usage_limit_notifications")
class ExternalProviderLimitNotificationEntity(
    @Column(name = "limit_policy_id", nullable = false)
    val limitPolicyId: Long,
    @Column(name = "period_start", nullable = false)
    val periodStart: LocalDate,
    @Column(name = "threshold_percent", nullable = false)
    val thresholdPercent: Int,
    @Column(name = "notified_at", nullable = false)
    val notifiedAt: Instant,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}

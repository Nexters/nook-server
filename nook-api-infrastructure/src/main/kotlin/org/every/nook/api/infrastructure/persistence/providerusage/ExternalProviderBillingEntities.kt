package org.every.nook.api.infrastructure.persistence.providerusage

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.infrastructure.persistence.BaseEntity
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(
    name = "external_provider_billing_snapshots",
    uniqueConstraints = [
        UniqueConstraint(
            name = "idx_u_provider_sku_period_start_period_end",
            columnNames = ["provider", "sku", "period_start", "period_end"],
        ),
    ],
    indexes = [Index(name = "idx_period_start_period_end", columnList = "period_start,period_end")],
)
class ExternalProviderBillingSnapshotEntity(
    @Column(name = "provider", nullable = false, length = 50)
    val provider: String,
    @Column(name = "sku", nullable = false, length = 100)
    val sku: String,
    @Column(name = "period_start", nullable = false)
    val periodStart: LocalDate,
    @Column(name = "period_end", nullable = false)
    val periodEnd: LocalDate,
    @Column(name = "usage_units", nullable = false, precision = 20, scale = 6)
    var usageUnits: BigDecimal,
    @Column(name = "cost_usd", nullable = false, precision = 20, scale = 8)
    var costUsd: BigDecimal,
    @Column(name = "source", nullable = false, length = 100)
    var source: String,
    @Column(name = "source_updated_at", nullable = false)
    var sourceUpdatedAt: Instant,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set

    fun update(usageUnits: BigDecimal, costUsd: BigDecimal, source: String, sourceUpdatedAt: Instant) {
        this.usageUnits = usageUnits
        this.costUsd = costUsd
        this.source = source
        this.sourceUpdatedAt = sourceUpdatedAt
    }
}

@Entity
@Table(
    name = "external_provider_billing_sync_states",
    uniqueConstraints = [UniqueConstraint(name = "idx_u_provider", columnNames = ["provider"])],
)
class ExternalProviderBillingSyncStateEntity(
    @Column(name = "provider", nullable = false, length = 50)
    val provider: String,
    @Column(name = "status", nullable = false, length = 20)
    var status: String = "NEVER_SYNCED",
    @Column(name = "last_attempted_at", nullable = true)
    var lastAttemptedAt: Instant? = null,
    @Column(name = "last_succeeded_at", nullable = true)
    var lastSucceededAt: Instant? = null,
    @Column(name = "error_message", nullable = true, length = 500)
    var errorMessage: String? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set

    fun attempted(at: Instant) {
        lastAttemptedAt = at
    }

    fun succeeded(at: Instant) {
        status = "SUCCEEDED"
        lastAttemptedAt = at
        lastSucceededAt = at
        errorMessage = null
    }

    fun failed(at: Instant, message: String) {
        status = "FAILED"
        lastAttemptedAt = at
        errorMessage = message.take(MAX_ERROR_MESSAGE_LENGTH)
    }

    private companion object {
        const val MAX_ERROR_MESSAGE_LENGTH = 500
    }
}
